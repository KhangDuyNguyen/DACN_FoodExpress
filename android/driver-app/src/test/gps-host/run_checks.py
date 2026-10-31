"""Standalone JVM/mock GPS checks, without changing Gradle dependencies.

Run: python android/driver-app/src/test/gps-host/run_checks.py
Before-fix proof: append --baseline-ref cc2c70d (git show only; no checkout).
Requires JDK 17+ and jars cached by the project's Gradle build. No network/DB.
Compiles real VM, repositories, API DTOs and GPS policy. Only platform, session
and Profile IO are doubled. Outputs go to driver-app/build/gps-regression.
"""
import argparse
import hashlib
import os
from pathlib import Path
import shutil
import subprocess
import sys

FIXTURES = {
    "Lifecycle.kt": """
package androidx.lifecycle
import kotlinx.coroutines.*
lateinit var testDispatcher: CoroutineDispatcher
private val scopes = mutableMapOf<ViewModel, CoroutineScope>()
open class ViewModel { protected open fun onCleared() {} }
val ViewModel.viewModelScope: CoroutineScope get() = scopes.getOrPut(this) {
    CoroutineScope(SupervisorJob() + testDispatcher)
}
fun finishTestScopes() { scopes.values.forEach { it.cancel() }; scopes.clear() }
""",
    "Auth.kt": """
package vn.hutech.foodexpress.driver.data.auth
class DriverAuthRepository(var token: String? = "fixture-token") {
    internal fun currentAccessToken() = token
}
""",
    "Profile.kt": """
package vn.hutech.foodexpress.driver.data.profile
import kotlinx.coroutines.CompletableDeferred
sealed interface DriverProfileResult {
    data class Loaded(val profile: DriverProfile): DriverProfileResult
    data object SessionExpired: DriverProfileResult
    data class Failed(val message: String): DriverProfileResult
}
class DriverProfileRepository(var profile: DriverProfile, val calls: MutableList<String>) {
    var gate: CompletableDeferred<Unit>? = null
    suspend fun getProfile(): DriverProfileResult {
        calls.add("GET"); gate?.await(); return DriverProfileResult.Loaded(profile)
    }
}
""",
    "Inject.kt": "package javax.inject\nannotation class Inject\nannotation class Singleton\n",
    "Dagger.kt": "package dagger\nannotation class Module\nannotation class Provides\n",
    "Install.kt": "package dagger.hilt\nimport kotlin.reflect.KClass\nannotation class InstallIn(vararg val value: KClass<*>)\n",
    "Component.kt": "package dagger.hilt.components\nclass SingletonComponent\n",
    "HiltVm.kt": "package dagger.hilt.android.lifecycle\nannotation class HiltViewModel\n",
}
SENSOR = """
// Sensor and monotonic clock double. Policy/models above come from production.
class DriverForegroundLocationSource(val clock: () -> Long) {
    var status = DriverGpsStatus.READY
    var started = false
    var fixed: DriverLocationFix? = null
    fun nowNanos() = clock()
    fun start(): DriverGpsSnapshot { started = true; return refresh() }
    fun stop() { started = false }
    fun refresh(): DriverGpsSnapshot {
        if (!started) return DriverGpsSnapshot()
        if (status != DriverGpsStatus.READY) return DriverGpsSnapshot(status)
        val fix = fixed ?: DriverLocationFix(10.0, 106.0, nowNanos(), 1_700_000_000_000L, 5f)
        return DriverGpsSnapshot(DriverLocationPolicy.problem(fix, nowNanos()) ?: DriverGpsStatus.READY, fix)
    }
}
"""


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--baseline-ref", help="Read only the VM from this Git revision")
    args = parser.parse_args()
    here = Path(__file__).resolve().parent
    module = here.parents[2]
    repo = module.parents[1]
    base = module / "src/main/java/vn/hutech/foodexpress/driver"
    out = module / "build/gps-regression" / ("before" if args.baseline_ref else "after")
    out.mkdir(parents=True, exist_ok=True)
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"

    def jar(group, name, version):
        matches = sorted(p for p in (cache / group / name / version).rglob("*.jar")
                         if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
        if not matches:
            raise RuntimeError(f"Missing cached {group}:{name}:{version}; build with Gradle first")
        return str(matches[0])

    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java.exe") if os.name == "nt" and os.environ.get("JAVA_HOME") else shutil.which("java")
    if not java:
        raise RuntimeError("JDK java not found")
    def kotlin(name):
        return jar("org.jetbrains.kotlin", name, "2.2.21")
    runtime = [kotlin("kotlin-stdlib"), jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2"),
               jar("com.squareup.retrofit2", "retrofit", "3.0.0"), jar("com.squareup.okhttp3", "okhttp", "4.12.0"),
               jar("com.squareup.okio", "okio-jvm", "3.15.0")]
    compiler = [kotlin(n) for n in ("kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime", "kotlin-daemon-embeddable")]
    compiler += [jar("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"), runtime[1], jar("org.jetbrains", "annotations", "23.0.0")]
    inputs = [base / p for p in (
        "data/status/DriverStatusApi.kt", "data/status/DriverStatusRepository.kt",
        "data/location/DriverLocationApi.kt", "data/location/DriverLocationRepository.kt",
        "data/profile/DriverProfileApi.kt")]
    vm = base / "ui/dashboard/DriverDashboardViewModel.kt"
    vm_text = (subprocess.check_output(["git", "show", f"{args.baseline_ref}:{vm.relative_to(repo).as_posix()}"], cwd=repo).decode("utf-8")
               if args.baseline_ref else vm.read_text(encoding="utf-8"))
    vm_copy = out / vm.name
    vm_copy.write_text(vm_text, encoding="utf-8")
    inputs.append(vm_copy)
    source = (base / "data/location/DriverForegroundLocationSource.kt").read_text(encoding="utf-8")
    policy = source[source.index("object DriverLocationPolicy"):source.index("// Owned by the Activity ViewModel")]
    fixtures = dict(FIXTURES, Source="package vn.hutech.foodexpress.driver.data.location\n" + policy + SENSOR)
    for name, content in fixtures.items():
        path = out / (name if name.endswith(".kt") else name + ".kt")
        path.write_text(content, encoding="utf-8")
        inputs.append(path)
    inputs += [here / "DriverDashboardGpsChecks.kt", module / "src/test/java/vn/hutech/foodexpress/driver/data/location/DriverLocationPolicyChecks.kt"]
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    command = [java, "-cp", os.pathsep.join(compiler), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
               "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", os.pathsep.join(runtime),
               "-d", str(classes)] + list(map(str, inputs))
    compiled = subprocess.run(command, capture_output=True, encoding="utf-8", errors="replace")
    (out / "compile.log").write_text(compiled.stdout + compiled.stderr, encoding="utf-8")
    if compiled.returncode:
        print(compiled.stdout + compiled.stderr)
        return compiled.returncode
    report = [f"VM sha256={hashlib.sha256(vm_text.encode()).hexdigest()}",
              f"Mode={'baseline ' + args.baseline_ref if args.baseline_ref else 'working source'}; JVM/mock only"]
    result = 0
    for entry in ("vn.hutech.foodexpress.driver.data.location.DriverLocationPolicyChecks", "DriverDashboardGpsChecksKt"):
        run = subprocess.run([java, "-cp", os.pathsep.join([str(classes)] + runtime), entry],
                             capture_output=True, encoding="utf-8", errors="replace")
        report.append(run.stdout + run.stderr)
        result = result or run.returncode
    text = "\n".join(report)
    (out / "results.log").write_text(text, encoding="utf-8")
    print(text)
    return result


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())

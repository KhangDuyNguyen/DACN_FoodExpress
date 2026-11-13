"""5D-1 checks for delivery commands, current-order races and session guards, with platform/session doubles.

Run: python android/driver-app/src/test/delivery-host/run_checks.py
Uses the existing Gradle cache, JDK 21, and no additional dependencies. Local HTTP
fixtures are NOT the Food Express Backend. Outputs: driver-app/build/delivery-checks.
"""
import importlib.util
import os
from pathlib import Path
import shutil
import subprocess
import sys


def main():
    here = Path(__file__).resolve().parent
    module = here.parents[2]
    base = module / "src/main/java/vn/hutech/foodexpress/driver"
    out = module / "build/delivery-checks"
    out.mkdir(parents=True, exist_ok=True)
    cache = Path(os.environ.get("GRADLE_USER_HOME", Path.home() / ".gradle")) / "caches/modules-2/files-2.1"
    spec = importlib.util.spec_from_file_location("gps_checks", here.parent / "gps-host/run_checks.py")
    gps = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(gps)
    fixtures = {name: value for name, value in gps.FIXTURES.items() if name != "Profile.kt"}
    fixtures["Lifecycle.kt"] += """
class SavedStateHandle(private val values: MutableMap<String, Any?> = mutableMapOf()) {
    @Suppress("UNCHECKED_CAST") operator fun <T> get(key: String): T? = values[key] as T?
    operator fun <T> set(key: String, value: T) { values[key] = value }
    @Suppress("UNCHECKED_CAST") fun <T> remove(key: String): T? = values.remove(key) as T?
}
"""
    fixtures["SystemClock.kt"] = "package android.os\nobject SystemClock { fun elapsedRealtime(): Long = error(\"Inject the test clock\") }\n"

    def jar(group, name, version):
        matches = sorted(p for p in (cache / group / name / version).rglob("*.jar")
                         if not p.name.endswith(("-sources.jar", "-javadoc.jar")))
        if not matches:
            raise RuntimeError(f"Missing cached {group}:{name}:{version}; build with Gradle first")
        return str(matches[0])

    def kotlin(name):
        return jar("org.jetbrains.kotlin", name, "2.2.21")

    java = str(Path(os.environ["JAVA_HOME"]) / "bin/java.exe") if os.name == "nt" and os.environ.get("JAVA_HOME") else shutil.which("java")
    runtime = [kotlin("kotlin-stdlib"), jar("org.jetbrains.kotlinx", "kotlinx-coroutines-core-jvm", "1.10.2"),
               jar("com.squareup.retrofit2", "retrofit", "3.0.0"), jar("com.squareup.okhttp3", "okhttp", "4.12.0"),
               jar("com.squareup.okio", "okio-jvm", "3.15.0"), jar("com.squareup.retrofit2", "converter-gson", "3.0.0"),
               jar("com.google.code.gson", "gson", "2.13.1")]
    compiler = [kotlin(n) for n in ("kotlin-compiler-embeddable", "kotlin-stdlib", "kotlin-script-runtime", "kotlin-daemon-embeddable")]
    compiler += [jar("org.jetbrains.kotlin", "kotlin-reflect", "1.6.10"), runtime[1], jar("org.jetbrains", "annotations", "23.0.0")]
    inputs = [base / p for p in ("data/offer/DriverOfferApi.kt", "data/offer/DriverOfferRepository.kt",
                                "ui/offer/OfferDeadline.kt", "ui/offer/DriverOffersViewModel.kt",
                                "data/order/DriverOrderApi.kt", "data/order/DriverOrderRepository.kt",
                                "ui/order/DriverCurrentOrderViewModel.kt")]
    for name, value in fixtures.items():
        path = out / name
        path.write_text(value, encoding="utf-8")
        inputs.append(path)
    inputs.append(here / "DriverDeliveryChecks.kt")
    inputs.append(here.parent / "current-order-host/VirtualClock.kt")
    classes = out / "classes"
    classes.mkdir(exist_ok=True)
    command = [java, "-cp", os.pathsep.join(compiler), "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
               "-no-stdlib", "-no-reflect", "-jvm-target", "17", "-classpath", os.pathsep.join(runtime),
               "-d", str(classes)] + list(map(str, inputs))
    built = subprocess.run(command, capture_output=True, encoding="utf-8", errors="replace")
    (out / "compile.log").write_text(built.stdout + built.stderr, encoding="utf-8")
    if built.returncode:
        print(built.stdout + built.stderr)
        return built.returncode
    run = subprocess.run([java, "--add-modules", "jdk.httpserver", "-cp", os.pathsep.join([str(classes)] + runtime), "DriverDeliveryChecksKt"],
                         capture_output=True, encoding="utf-8", errors="replace", timeout=90)
    report = "Real Kotlin VM/Repository/Retrofit; fake Android lifecycle/session/clock and local HTTP fixtures.\n" + run.stdout + run.stderr
    (out / "results.log").write_text(report, encoding="utf-8")
    print(report)
    return run.returncode


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8")
    raise SystemExit(main())

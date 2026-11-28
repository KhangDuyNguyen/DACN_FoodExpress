package vn.hutech.fooddelivery.notification;

import com.google.auth.oauth2.GoogleCredentials;
import com.google.firebase.FirebaseApp;
import com.google.firebase.FirebaseOptions;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.AndroidConfig;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
class FirebaseCustomerPush {
    private final String projectId;
    private FirebaseApp app;

    FirebaseCustomerPush(@Value("${app.fcm.project-id:}") String projectId) {
        this.projectId = projectId.strip();
    }

    boolean enabled() {
        return !projectId.isEmpty();
    }

    boolean sendOrderUpdate(String token) throws IOException, FirebaseMessagingException {
        var message = Message.builder().setToken(token)
                .setNotification(Notification.builder()
                        .setTitle("Đơn hàng vừa cập nhật")
                        .setBody("Mở Food Express để xem trạng thái mới.")
                        .build())
                .build();
        return send(message);
    }

    boolean sendInbox(String token, long notificationId, String eventType, Long ttlMillis)
            throws IOException, FirebaseMessagingException {
        return send(inboxMessage(token, notificationId, eventType, ttlMillis));
    }

    static Message inboxMessage(String token, long notificationId, String eventType, Long ttlMillis) {
        var message = Message.builder().setToken(token)
                .putData("notificationId", Long.toString(notificationId))
                .putData("eventType", eventType);
        if (ttlMillis != null) {
            message.setAndroidConfig(AndroidConfig.builder().setTtl(ttlMillis).build());
        }
        return message.build();
    }

    private boolean send(Message message) throws IOException, FirebaseMessagingException {
        try {
            messaging().send(message);
            return true;
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                return false;
            }
            throw e;
        }
    }

    private synchronized FirebaseMessaging messaging() throws IOException {
        if (app == null) {
            var options = FirebaseOptions.builder()
                    .setProjectId(projectId)
                    .setCredentials(GoogleCredentials.getApplicationDefault())
                    .setConnectTimeout(3500)
                    .setReadTimeout(3500)
                    .build();
            app = FirebaseApp.initializeApp(options, "foodexpress-customer-push");
        }
        return FirebaseMessaging.getInstance(app);
    }

    @PreDestroy
    synchronized void close() {
        if (app != null) {
            app.delete();
            app = null;
        }
    }
}

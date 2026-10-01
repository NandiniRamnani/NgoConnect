package app.service;

import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/**
 * Thin wrapper around JavaMailSender. Returns false instead of throwing so
 * callers can decide whether a failed send should be fatal (e.g. password
 * reset — the user needs to know) or best-effort (e.g. a status notification
 * for an action that already succeeded).
 */
@Service
public class MailService {
    private final JavaMailSender mailSender;

    public MailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public boolean send(String to, String subject, String body) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setTo(to);
        message.setSubject(subject);
        message.setText(body);
        try {
            mailSender.send(message);
            return true;
        } catch (MailException e) {
            System.err.println("Failed to send email to " + to + ": " + e.getMessage());
            return false;
        }
    }

    /**
     * Same idea, but with a file attached — used to deliver 80G receipts.
     *
     * SimpleMailMessage above cannot do this. An email carrying both a message and a file is a
     * MIME "multipart" — the text and the PDF are separate labelled sections of one message — and
     * SimpleMailMessage only knows how to write a plain single-part email. MimeMessage is the
     * richer form, and MimeMessageHelper is Spring's wrapper that assembles the parts correctly
     * so we do not hand-build MIME headers.
     *
     * The `true` passed to the helper's constructor is what switches multipart mode on; without
     * it, addAttachment throws.
     *
     * The bytes are wrapped in a ByteArrayResource rather than written to a temp file first,
     * because the PDF was generated in memory and never needs to touch the disk.
     *
     * @param fileName what the attachment is called in the recipient's inbox
     * @param content  the raw file bytes
     */
    public boolean sendWithAttachment(String to, String subject, String body,
                                      String fileName, byte[] content) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true); // true = multipart
            helper.setTo(to);
            helper.setSubject(subject);
            helper.setText(body);
            helper.addAttachment(fileName, new ByteArrayResource(content), "application/pdf");
            mailSender.send(message);
            return true;
        } catch (Exception e) {
            // Deliberately catching Exception, not just MailException: building a MimeMessage can
            // throw MessagingException too, and a caller treating this as best-effort should not
            // have to care which of the two failed.
            System.err.println("Failed to send email with attachment to " + to + ": " + e.getMessage());
            return false;
        }
    }
}

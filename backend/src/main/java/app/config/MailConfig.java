package app.config;

import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.mail.javamail.JavaMailSenderImpl;
import org.springframework.stereotype.Component;

/**
 * Strips whitespace out of the SMTP password after Spring has built the mail sender.
 *
 * WHY THIS EXISTS
 * Gmail requires an "App Password" for SMTP, and Google presents it on screen as four groups of
 * four characters separated by spaces — "abcd efgh ijkl mnop" — purely so it can be read and
 * transcribed accurately. Those spaces are not part of the credential. Copying it the obvious way
 * therefore produces a nineteen-character string that Google rejects with a bare
 * "535 Username and Password not accepted", an error that points at the credential being wrong
 * rather than at its formatting. It is a genuinely confusing failure, and it recurs every time the
 * value is entered somewhere new: another laptop, a teammate's machine, a hosting provider's
 * environment-variable form.
 *
 * Removing the whitespace here closes that trap permanently, and nothing legitimate is lost
 * because an SMTP password cannot contain spaces in the first place.
 *
 * WHY A BeanPostProcessor RATHER THAN A REPLACEMENT BEAN
 * The obvious approach — declaring our own @Primary JavaMailSender — does not work. Spring Boot's
 * mail auto-configuration is conditional on no JavaMailSender already being defined, so supplying
 * one makes the entire auto-configuration back off, taking MailProperties with it. The
 * replacement would then have to re-read and re-apply every mail setting by hand, and would
 * silently drop any it forgot.
 *
 * A BeanPostProcessor avoids all of that. Auto-configuration runs exactly as normal and sets up
 * host, port, encoding, protocol and the smtp.* properties; this then adjusts the one field that
 * needs correcting, just after the bean is initialised and before anything uses it.
 */
@Component
public class MailConfig implements BeanPostProcessor {

    @Override
    public Object postProcessAfterInitialization(Object bean, String beanName) {
        if (bean instanceof JavaMailSenderImpl sender) {
            String password = sender.getPassword();
            if (password != null) {
                // \s covers ordinary spaces, tabs, and the non-breaking space that often comes
                // along when text is copied out of a web page.
                String cleaned = password.replaceAll("\\s", "");
                if (!cleaned.equals(password)) {
                    sender.setPassword(cleaned);
                    System.out.println("MailConfig: removed " + (password.length() - cleaned.length())
                            + " whitespace character(s) from the SMTP password "
                            + "(Google displays App Passwords in spaced groups; the spaces are not part of it)");
                }
            }
        }
        return bean;
    }
}

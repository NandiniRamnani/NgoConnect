package app.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Shouts in the startup log when payments are being simulated.
 *
 * A setting that changes whether money is real should never be something you have to go and check.
 * The dangerous failure here is silent: a server left with PAYMENT_DEMO_MODE=true would accept
 * donations nobody paid for, credit NGO balances that could then be withdrawn, and issue 80G tax
 * receipts for payments that never happened — all while looking completely normal.
 *
 * Printing it unmissably at boot means the state is visible in the first screen of any log, on any
 * machine, without anyone remembering to look.
 */
@Component
public class DemoModeBanner {

    @Value("${payments.demo-mode:false}")
    private boolean demoMode;

    /**
     * ApplicationReadyEvent rather than @PostConstruct: it fires after the context is fully built
     * and Spring has finished its own logging, so this lands at the very bottom of the startup
     * output where it is actually read, instead of being buried mid-way up.
     */
    @EventListener(ApplicationReadyEvent.class)
    public void announce() {
        if (!demoMode) return;

        System.out.println();
        System.out.println("  ##################################################################");
        System.out.println("  #                                                                #");
        System.out.println("  #   PAYMENTS ARE IN DEMO MODE - NO REAL MONEY IS BEING HANDLED   #");
        System.out.println("  #                                                                #");
        System.out.println("  #   Razorpay is not contacted and no signature is verified.       #");
        System.out.println("  #   Every donation and top-up will succeed automatically, and     #");
        System.out.println("  #   80G receipts will be issued for payments that never happened. #");
        System.out.println("  #                                                                #");
        System.out.println("  #   Set PAYMENT_DEMO_MODE=false in .env for real payments.        #");
        System.out.println("  #                                                                #");
        System.out.println("  ##################################################################");
        System.out.println();
    }
}

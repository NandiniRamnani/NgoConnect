package app.model;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * A named number that only ever goes up. One document per sequence, the document's _id being the
 * sequence's name — so "receipt:2026-27" is one row holding the last receipt number issued in that
 * financial year.
 *
 * WHY A COLLECTION AND NOT A JAVA COUNTER
 * A receipt number has to be unique and unbroken: the tax office reads a gap or a repeat as a
 * missing or duplicated donation. A static int in the application would restart at zero on every
 * redeploy and would count separately in each running instance. Doing it as "read the highest
 * number, add one, save" has the same race that makes wallet balances go wrong — two donations
 * completing at once both read 41 and both become 42.
 *
 * Keeping the number in MongoDB and incrementing it with $inc makes the read-and-increment a
 * single indivisible operation, so every caller is handed a different number no matter how many
 * arrive together. It is the same tool used for balances in WalletService, applied to a different
 * kind of value.
 */
@Document(collection = "counters")
public class Counter {

    /** The sequence name, e.g. "receipt:2026-27". Doubles as the primary key. */
    @Id
    private String id;

    /** The last value handed out. The next caller gets this + 1. */
    private long seq;

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public long getSeq() { return seq; }
    public void setSeq(long seq) { this.seq = seq; }
}

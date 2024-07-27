package net.shihome.nt.comm.id;

import net.shihome.nt.comm.utils.IpUtil;
import org.springframework.util.IdGenerator;

import java.net.InetAddress;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicStampedReference;

/**
 * {@code @Description:} <span>id generator based on snowflake algorithm 1 bit sign bit + 41 bit
 * timestamp + 10 bit machine id + 12 bit serial number, a total of 64 bits long type id</span>
 */
public class SnowFlowerIdGenerator implements IdGenerator {

    // start from 2022-01-01
    private static final long EPOCH = 1640966400000L;

    private static final long WORKER_ID_BITS = 10L;

    private static final long SEQUENCE_BITS = 12L;

    private static final long OFFSET_EPOCH = WORKER_ID_BITS + SEQUENCE_BITS;

    private static final long MAX_WORKER_ID = ~(-1L << WORKER_ID_BITS);

    private static final long OFFSET_WORKER_ID = SEQUENCE_BITS;

    private static final long MAX_SEQUENCE = -1L << SEQUENCE_BITS;

    private final AtomicStampedReference<StampedSequence> lastStampedSequenceReference;

    private final long workerId;

    private final long mostSigBits = System.currentTimeMillis();

    /** By default, the server ip is used as the worker id */
    public SnowFlowerIdGenerator() {
        this.workerId = initWorkerId();
        StampedSequence stampedSequence = new StampedSequence(EPOCH, 0);
        lastStampedSequenceReference = new AtomicStampedReference<>(stampedSequence, 0);
    }

    public SnowFlowerIdGenerator(long workerId) {
        if (workerId < 0 || workerId > MAX_WORKER_ID) {
            throw new IllegalArgumentException(
                    String.format("worker Id can't be greater than %d or less than 0", MAX_WORKER_ID));
        }
        this.workerId = workerId;
        StampedSequence stampedSequence = new StampedSequence(EPOCH, 0);
        lastStampedSequenceReference = new AtomicStampedReference<>(stampedSequence, 0);
    }

    protected UUID generatorId(long timestamp, long sequence) {
        long l = (timestamp - EPOCH) << (OFFSET_EPOCH) | this.workerId << OFFSET_WORKER_ID | sequence;
        return new UUID(mostSigBits, l);
    }

    @Override
    public UUID generateId() {
        int[] versionHolder = new int[1];
        while (true) {
            StampedSequence lastStampedSequence = lastStampedSequenceReference.get(versionHolder);
            long lastTimestamp = lastStampedSequence.timestamp;
            AtomicLong lastSequence = lastStampedSequence.sequence;
            long currentTimestamp = this.timeGen();
            // Check if the server time is move backwards
            int compare = Long.compare(currentTimestamp, lastTimestamp);
            if (compare <= 0) {
                long sequence = lastSequence.getAndIncrement();
                if ((sequence & MAX_SEQUENCE) == 0L) {
                    return generatorId(lastTimestamp, sequence);
                } else {
                    long newFutureTimestamp = lastTimestamp + 1;
                    StampedSequence nextSequence = new StampedSequence(newFutureTimestamp, 0);
                    boolean set =
                            lastStampedSequenceReference.compareAndSet(
                                    lastStampedSequence, nextSequence, versionHolder[0], versionHolder[0] + 1);
                    if (set) {
                        sequence = nextSequence.sequence.getAndIncrement();
                        if ((sequence & MAX_SEQUENCE) == 0L) {
                            return generatorId(newFutureTimestamp, sequence);
                        }
                    }
                }
            } else {
                StampedSequence nextSequence = new StampedSequence(currentTimestamp, 0);
                if (lastStampedSequenceReference.compareAndSet(
                        lastStampedSequence, nextSequence, versionHolder[0], versionHolder[0] + 1)) {
                    long sequence = nextSequence.sequence.getAndIncrement();
                    if ((sequence & MAX_SEQUENCE) == 0L) {
                        return generatorId(currentTimestamp, sequence);
                    } else {
                        long newFutureTimestamp = currentTimestamp + 1;
                        StampedSequence nextSequence2 = new StampedSequence(newFutureTimestamp, 0);
                        if (lastStampedSequenceReference.compareAndSet(
                                nextSequence, nextSequence2, versionHolder[0] + 1, versionHolder[0] + 2)) {
                            sequence = nextSequence2.sequence.getAndIncrement();
                            if ((sequence & MAX_SEQUENCE) == 0L) {
                                return generatorId(newFutureTimestamp, sequence);
                            }
                        }
                    }
                }
            }
        }
    }

    protected long timeGen() {
        return System.currentTimeMillis();
    }

    /**
     * {@code @Description:} Generate work id based on local ip
     *
     * <p>The machines in CIDR 0.0.0.0/22 network segment are not unique
     *
     * <p>{@code @return:} void
     */
    private long initWorkerId() {
        InetAddress address = IpUtil.getLocalAddress();
        byte[] ipAddressByteArray = address.getAddress();
        return (((ipAddressByteArray[ipAddressByteArray.length - 2] & 0B11) << Byte.SIZE)
                + (ipAddressByteArray[ipAddressByteArray.length - 1] & 0xFF));
    }

    /** {@code @Description:} Save timestamp and sequence number for atomic operations */
    static class StampedSequence {

        private final AtomicLong sequence;
        private final long timestamp;

        public StampedSequence(long timestamp, long sequence) {
            this.timestamp = timestamp;
            this.sequence = new AtomicLong(sequence);
        }
    }
}

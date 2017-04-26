import java.net.InetAddress;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.*;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/*
 * The AddressCache has a max age for the elements it's storing, an add method
 * for adding elements, a remove method for removing, a peek method which
 * returns the most recently added element, and a take method which removes
 * and returns the most recently added element.
 */
public class AddressCache {

    public ConcurrentHashMap<InetAddress, Long> cacheTimeMap = new ConcurrentHashMap<>();    // To store list of InetAdress

    public ConcurrentLinkedDeque<InetAddress> cacheQueue = new ConcurrentLinkedDeque<>();    // To store Inet address and Entry Time

    private final ScheduledExecutorService threadScheduler = Executors
            .newSingleThreadScheduledExecutor(new MyThreadFactory(true));

    private final class MyThreadFactory implements ThreadFactory {

        private boolean isDaemon = false;

        public MyThreadFactory(boolean daemon){
            isDaemon = daemon;
        }

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r);
            t.setDaemon(isDaemon);
            return t;
        }

    }

    private final ReentrantReadWriteLock lock = new ReentrantReadWriteLock();

    private  Condition isEmptyCondition;

    private long maxAge;

    private TimeUnit timeUnit;


    /**
     * Creates a new instance of AddressCache using default values of scheduler
     *
     * @param maxAge - The Expiry time for all the elements of the deque
     * @param unit   - Time unit
     */
    public AddressCache(long maxAge, TimeUnit unit) {
        this(maxAge, CacheConstants.INITIAL_DELAY, CacheConstants.CLEANUP_JOB_Frequency, unit);
    }



    /**
     *  Creates a new instance of AddressCache using the supplied max age , initial delay and cleanup frequency
     *  If Scheduler values are passed 0, Schedule thread is not initialized
     *
     * @param maxAge        The Expiry time for all the elements of the deque
     * @param cleanupFrequency- schedule time for Cleanup job to delete expired entries
     * @param initialDelay- Intial delay for the cleanup job to start after AddressCache is Initialization
     * @param unit-         Time unit
     */
    public AddressCache(long maxAge, long initialDelay, long cleanupFrequency,
                        TimeUnit unit) {

        if (initialDelay != 0 && cleanupFrequency != 0) {
            threadScheduler.scheduleWithFixedDelay(cleanUp, initialDelay,
                    cleanupFrequency, unit);
        }

        this.timeUnit = unit;
        this.maxAge = TimeUnit.NANOSECONDS.convert(maxAge, this.timeUnit);
        isEmptyCondition = lock.writeLock().newCondition();

    }

    /**
     * add() method must store unique elements only (existing elements must be
     * ignored). This will return true if the element was successfully added.
     *
     * @param address
     * @return
     */
    public boolean add(InetAddress address) {

        if (address == null)
            throw new NullPointerException("Address cannot be null");

        boolean action;
        boolean isEmpty = isQueueEmpty();

        lock.readLock().lock();

        if (cacheTimeMap.put(address, System.nanoTime()) == null) // In case of Unique Address
        {
            action = cacheQueue.add(address);
        } else {
            // Address is already present, remove entry and then add with updated time
            cacheQueue.remove(address);
            action = cacheQueue.add(address);
        }

        lock.readLock().unlock();

        if (isEmpty && action) {
            lock.writeLock().lock();
            isEmptyCondition.signalAll();
            lock.writeLock().unlock();
        }

        return action;
    }

    /**
     * remove() method will return true if the address was successfully removed
     *
     * @param address
     * @return
     */
    public boolean remove(InetAddress address) {

        boolean action=false;

        lock.readLock().lock();

        if (cacheTimeMap.remove(address)!=null)
            action= cacheQueue.remove(address);

        lock.readLock().unlock();
        return action;
    }

    /**
     * The peek() method will return the most recently added element, null if no
     * element exists.
     *
     * @return
     */
    public InetAddress peek() {

        InetAddress address;

        lock.readLock().lock();
        address = cacheQueue.peekLast();
        lock.readLock().unlock();

        if (address!=null && IsExpired(address))
            // Whole Cache is expired, Nothing to peek
            address = null;

        return address;
    }

    /**
     * take() method retrieves and removes the most recently added element from
     * the cache and waits if necessary until an element becomes available.
     *
     * @return
     */
    public InetAddress take() {
        InetAddress address=null;
        lock.readLock().lock();

        if (isQueueEmpty()) {
            lock.readLock().unlock();
            waitTillAddressIsAvailable(lock, cacheQueue, isEmptyCondition);
        }

        lock.readLock().lock();
        address= cacheQueue.peekLast();
        cacheQueue.remove(address);
        lock.readLock().unlock();
        return address;

    }

    /**
     * Thread to cleanup expired Address from the cache
     * Before CLeaning up it takes write lock
     */
    private final Runnable cleanUp = new Runnable() {
        @Override
        public void run() {
            if (cacheQueue.isEmpty()) Thread.yield();

            lock.writeLock().lock();
            Set<InetAddress> addressSet = keySet();
            lock.writeLock().unlock();

            Set<InetAddress> markedForRemoval = getExpiredAddresses(addressSet);

            if (markedForRemoval.size() > 0) {
                lock.writeLock().lock();
                removeExpiredAddress(markedForRemoval);
                lock.writeLock().unlock();
            }

        }

    };

    private static void waitTillAddressIsAvailable(ReentrantReadWriteLock lock, ConcurrentLinkedDeque<InetAddress> addressQueue, Condition isEmptyCondition) {
        lock.writeLock().lock();
        try {
            // check if there' still no data
            while (addressQueue.size() == 0) {
                // will unlock and re-lock afte add method has signalled
                isEmptyCondition.await();
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        } finally {
            lock.writeLock().unlock();
        }
    }

    private boolean IsExpired(InetAddress address) {
        boolean flag = false;
        long initialTime;
        lock.readLock().lock();
        if (cacheTimeMap.containsKey(address)) {
            initialTime = cacheTimeMap.get(address);
            if ((System.nanoTime()-initialTime) > maxAge) flag = true;
        }
        lock.readLock().unlock();
        return flag;
    }

    public void clean() {

        if (isQueueEmpty()) return;
        else cleanUp.run();
        return;
    }

    private Set<InetAddress> keySet() {
        return cacheTimeMap.keySet();
    }

    private void removeExpiredAddress(Set<InetAddress> markedForRemoval) {
        for (InetAddress key : markedForRemoval) {
            boolean action = cacheQueue.remove(key);
            if (action)
                cacheTimeMap.remove(key);
        }
    }

    private Set<InetAddress> getExpiredAddresses( Set<InetAddress> AddressList) {
        Set<InetAddress> markedForRemoval = new HashSet<InetAddress>(10);
        for (InetAddress key : AddressList) {
            long initialTime = cacheTimeMap.get(key);
            if (initialTime == 0) {
                continue;
            }
            long interval = System.nanoTime() - initialTime;
            if (interval > maxAge) {
                markedForRemoval.add(key);
            }
        }
        return markedForRemoval;
    }
    private boolean isQueueEmpty() {
        lock.readLock().lock();
        boolean flag= cacheQueue.isEmpty();
        lock.readLock().unlock();
        return flag;
    }

}
import org.junit.Assert;
import org.junit.Test;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.TimeUnit;

/**
 * Created by KapilNanda on 22/04/17.
 */
public class TestMe {




    private final static int SLEEP_MULTIPLIER = 1000;

    @Test
    public void basicExpireThread() throws UnknownHostException,InterruptedException {
        AddressCache addressCache=new AddressCache(1000,500,1000,TimeUnit.MILLISECONDS);
        addressCache.add(InetAddress.getByName("0.0.0.1"));
        Thread.sleep(4*SLEEP_MULTIPLIER);
        InetAddress address = addressCache.peek();
        Assert.assertNull(address);
    }

    @Test
    public void multipleAddRemoveAddress() throws UnknownHostException,InterruptedException{
        AddressCache addressCache=new AddressCache(2000,500,1000,TimeUnit.MILLISECONDS);
        addressCache.add(InetAddress.getByName("0.0.0.1"));
        addressCache.add(InetAddress.getByName("0.0.0.2"));
        addressCache.add(InetAddress.getByName("0.0.0.1"));
        addressCache.add(InetAddress.getByName("0.0.0.3"));
        addressCache.add(InetAddress.getByName("0.0.0.4"));
        addressCache.remove(InetAddress.getByName("0.0.0.4"));
        Thread.sleep(2*SLEEP_MULTIPLIER);
        Assert.assertEquals(addressCache.cacheQueue.size(),3);

    }
    @Test
    public void basicPeekAddress() throws UnknownHostException,InterruptedException{
        AddressCache addressCache=new AddressCache(2000,4000,4000,TimeUnit.MILLISECONDS);
        addressCache.add(InetAddress.getByName("0.0.0.1"));
        Thread.sleep(2*SLEEP_MULTIPLIER);
        addressCache.add(InetAddress.getByName("0.0.0.2"));
        InetAddress address=addressCache.peek();
        Assert.assertEquals(address,InetAddress.getByName("0.0.0.2"));
    }
    @Test
    public void peekExpiredAddress() throws UnknownHostException,InterruptedException{
        AddressCache addressCache=new AddressCache(1000,4000,4000,TimeUnit.MILLISECONDS);
        addressCache.add(InetAddress.getByName("0.0.0.1"));
        addressCache.add(InetAddress.getByName("0.0.0.2"));
        Thread.sleep(2*SLEEP_MULTIPLIER);
        Assert.assertNull(addressCache.peek());
    }
    @Test
    public void takeAddress() throws UnknownHostException,InterruptedException{

        final AddressCache addressCache=new AddressCache(1000,4000,4000,TimeUnit.MILLISECONDS);

        Thread newThread=new Thread(new Runnable() {

            @Override
            public void run() {
                try {
                    Thread.sleep(1000);
                    addressCache.add(InetAddress.getByName("0.0.0.1"));
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }

            }
        });
        newThread.start();
        InetAddress inetAddress=  addressCache.take();
        Thread.sleep(2*SLEEP_MULTIPLIER);
        Assert.assertEquals(inetAddress,InetAddress.getByName("0.0.0.1"));
    }

    @Test
    public void manualCleanCache() throws UnknownHostException,InterruptedException{
        AddressCache addressCache=new AddressCache(1000,0,0,TimeUnit.MILLISECONDS);
        addressCache.add(InetAddress.getByName("0.0.0.1"));
        addressCache.add(InetAddress.getByName("0.0.0.2"));
        Thread.sleep(2*SLEEP_MULTIPLIER);
        addressCache.clean();
        Assert.assertEquals(addressCache.cacheQueue.size(),0);
    }

    @Test
    public void cacheWithDefaultScheduler() throws UnknownHostException,InterruptedException{
        AddressCache addressCache=new AddressCache(1000,TimeUnit.MILLISECONDS);
        addressCache.add(InetAddress.getByName("0.0.0.1"));
        addressCache.add(InetAddress.getByName("0.0.0.2"));
        Thread.sleep(3*SLEEP_MULTIPLIER);
        Assert.assertEquals(addressCache.cacheQueue.size(),0);
    }

    @Test(expected=NullPointerException.class)
    public void testNullInput() {
        AddressCache addressCache=new AddressCache(1000,TimeUnit.MILLISECONDS);
        addressCache.add(null);
    }

}


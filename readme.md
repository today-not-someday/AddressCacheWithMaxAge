# Address Cache with Max Age
Address Cache creates a cache of Inet Address with max age for its elements.
It supports 

  - Add - Adds address:Overwrite existing value
  - Remove - Removes Address passed to the method
  - Peek- Returns last value added (Not Expired). Does not remove
  - take- removes last value added, waits if cache is empty

#### Removing Expired addresses
  - A timetracker hashmap is created along with the cache
  - A cleanup job is run in the background to remove expired tokens
  - Cleanup job scheduler parameters can be passed while initializing the cache or default values will be used(if not passed).
  - To disable the background job, set job parametrs to 0. Cleanup method can be called to delete expired tokens
  
### INITIALIZATION

Address Cache can be initialized by:

1. Passing Max Age and Time unit in the construtor. Default parameters will be used for the cleanup job
```
$ public AddressCache(long maxAge, TimeUnit unit) {}
```
2. Passing Max Age, Initial delay , CleanupFrequency and TimeUnit
```
$ public AddressCache(long maxAge, long initialDelay, long cleanupFrequency, TimeUnit unit) {}
```
Note- Setting initialDelay and CleanupFrequency to 0 will disable the cleanup thread.

### Design

- I used a shared lock, readwrite lock. Cleanup thread is run with writeLock, add/remove/peek/take with readLock
  
- I have used ConcurrentLinkedDeque and ConcurrentHashMap to support multithread acces and modifications. 
- Have provided a cleanup method with the cache to manually delete expired addresses, in case job is disabled.
- Await and SignalALl is used to provide blocking behavior in the take method.

  

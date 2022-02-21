package shashok.concurrent.locker;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;
import shashok.concurrent.EntityLocker;
import shashok.concurrent.exception.EntityLockerException;

import java.util.concurrent.TimeUnit;

public class EntityLockerImplTest {
    private EntityLocker<Object> locker;
    private int entityId_1;
    private int entityId_2;
    private String entityId_3;

    @Before
    public void setUp() {
        locker = new EntityLockerImpl<>();
        entityId_1 = 1;
        entityId_2 = 2;
        entityId_3 = "3";
    }

    @Test(expected = EntityLockerException.class)
    public void TestUnlockingUnlockedEntity() throws EntityLockerException {
        locker.unlock(entityId_1);
    }

    @Test
    public void TestDeletion() throws EntityLockerException {
        locker.lock(entityId_1);
        Assert.assertTrue(locker.isLockedByCurrentThread(entityId_1));
        locker.unlock(entityId_1);
        Assert.assertFalse(locker.isLockedByCurrentThread(entityId_1));
    }

    @Test
    public void TestCorrectCount() throws EntityLockerException {
        locker.lock(entityId_1);
        Assert.assertEquals(1, locker.currentLocksCount());
        locker.lock(entityId_2);
        Assert.assertEquals(2, locker.currentLocksCount());
        locker.unlock(entityId_2);
        locker.unlock(entityId_1);
        Assert.assertEquals(0, locker.currentLocksCount());
    }

    @Test
    public void TestDeadlock() throws InterruptedException, EntityLockerException {
        Thread thread = new Thread(() -> {
            try {
                locker.lock(entityId_1);
                while (!locker.isLockedByAnotherThread(entityId_2)) {
                }
                locker.lock(entityId_2);
                TimeUnit.SECONDS.sleep(3);
                locker.unlock(entityId_2);
                locker.unlock(entityId_1);
            } catch (EntityLockerException | InterruptedException e) {}
        });
        thread.start();
        try {
            locker.lock(entityId_2);
            while (!locker.isLockedByAnotherThread(entityId_1)) {}
            TimeUnit.SECONDS.sleep(1);
            locker.lock(entityId_1);
        } catch (EntityLockerException e) {
            Assert.assertEquals(e.getMessage(),
                    "Thread " + Thread.currentThread().getName() + " cannot acquire lock on " + entityId_1
                            + " due to deadlock condition.");
        } finally {
            locker.unlock(entityId_2);
        }

        thread.join();
        Assert.assertEquals(0, locker.currentLocksCount());
    }

    @Test
    public void TestTryLock() throws EntityLockerException, InterruptedException {
        Thread thread = new Thread(() -> {
            try {
                locker.lock(entityId_1);
                TimeUnit.SECONDS.sleep(3);
                locker.unlock(entityId_1);
            } catch (EntityLockerException | InterruptedException e) {}
        });
        thread.start();
        while(!locker.isLockedByAnotherThread(entityId_1)){}
        boolean lockResult = locker.tryLock(entityId_1);
        Assert.assertFalse(lockResult);
        thread.join();
    }

    @Test
    public void TestTimeoutLock() throws EntityLockerException, InterruptedException {
        Thread thread = new Thread(() -> {
            try {
                locker.lock(entityId_1);
                TimeUnit.SECONDS.sleep(3);
                locker.unlock(entityId_1);
            } catch (EntityLockerException | InterruptedException e) {}
        });
        thread.start();
        while(!locker.isLockedByAnotherThread(entityId_1)){}
        boolean lockResult = locker.timeoutLock(entityId_1, 15, TimeUnit.MILLISECONDS);
        Assert.assertFalse(lockResult);
        lockResult = locker.timeoutLock(entityId_1, 5, TimeUnit.SECONDS);
        Assert.assertTrue(lockResult);
        thread.join();
        locker.unlock(entityId_1);
    }

    @Test
    public void TestGlobalLock() throws EntityLockerException, InterruptedException {
        locker.globalLock();
        Thread thread = new Thread(() -> {
            try {
                locker.lock(entityId_1);
                TimeUnit.SECONDS.sleep(3);
                locker.unlock(entityId_1);
            } catch (EntityLockerException | InterruptedException e) {}
        });
        thread.start();
        TimeUnit.MILLISECONDS.sleep(10);
        Assert.assertEquals(0, locker.currentLocksCount());
        locker.globalUnlock();
        while(!locker.isLockedByAnotherThread(entityId_1)){}
        Assert.assertEquals(1, locker.currentLocksCount());
        thread.join();
    }

    @Test
    public void TestTryGlobalLock() throws EntityLockerException, InterruptedException {
        Thread thread = new Thread(() -> {
            try {
                locker.lock(entityId_1);
                TimeUnit.SECONDS.sleep(3);
                locker.unlock(entityId_1);
            } catch (EntityLockerException | InterruptedException e) {}
        });
        thread.start();
        while(!locker.isLockedByAnotherThread(entityId_1)){}
        boolean tryLockResult = locker.tryGlobalLock();
        Assert.assertFalse(tryLockResult);
        thread.join();
    }

    @Test
    public void TestReentrant() throws EntityLockerException {
        locker.lock(entityId_1);
        Assert.assertEquals(1, locker.currentLocksCount());
        locker.lock(entityId_1);
        Assert.assertEquals(1, locker.currentLocksCount());
        locker.unlock(entityId_1);
        Assert.assertEquals(1, locker.currentLocksCount());
        locker.unlock(entityId_1);
        Assert.assertEquals(0, locker.currentLocksCount());
    }

    @Test
    public void TestDifferentTypes() throws EntityLockerException {
        locker.lock(entityId_1);
        locker.lock(entityId_3);
        Assert.assertEquals(2, locker.currentLocksCount());
        locker.unlock(entityId_3);
        locker.unlock(entityId_1);
    }
}

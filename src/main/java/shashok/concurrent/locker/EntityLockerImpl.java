package shashok.concurrent.locker;

import shashok.concurrent.EntityLocker;
import shashok.concurrent.exception.EntityLockerException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.function.Function;

public class EntityLockerImpl<T> implements EntityLocker<T> {

    private final Map<T, ReentrantLock> entityLockMap;

    private final ReentrantReadWriteLock globalLock;

    private final Map<T, Thread> entitiesLockedBy;
    private final Map<Thread, T> waitingThreads;
    private final Map<Thread, Set<T>> threadsLocks;

    private volatile Thread globalLockThread;

    public EntityLockerImpl() {
        entityLockMap = new ConcurrentHashMap<>();
        globalLock = new ReentrantReadWriteLock();

        entitiesLockedBy = new HashMap<>();
        threadsLocks = new HashMap<>();
        waitingThreads = new HashMap<>();
    }

    @Override
    public void lock(T entityId) throws EntityLockerException {
        generalLock(entityId, lock -> {
            lock.lock();
            return true;
        });
    }

    @Override
    public boolean tryLock(T entityId) throws EntityLockerException {
        return generalLock(entityId, Lock::tryLock);
    }

    @Override
    public boolean timeoutLock(T entityId, long timeout, TimeUnit timeUnit) throws EntityLockerException {
        return generalLock(entityId, lock -> {
            try {
                return lock.tryLock(timeout, timeUnit);
            } catch(InterruptedException e) {
                return false;
            }
        });
    }

    @Override
    public void unlock(T entityId) throws EntityLockerException {
        final Thread currentThread = Thread.currentThread();
        final ReentrantLock lock = entityLockMap.getOrDefault(entityId, null);
        if (lock == null) {
            throw new EntityLockerException(noLocksError(entityId));
        }
        if (!lock.isHeldByCurrentThread()) {
            throw new EntityLockerException(incorrectUnlockingThread(entityId, currentThread));
        }
        if (lock.getHoldCount() == 1) {
            synchronized (this) {
                threadsLocks.get(currentThread).remove(entityId);
                if (threadsLocks.get(currentThread).isEmpty()) {
                    threadsLocks.remove(currentThread);
                }
                entityLockMap.remove(entityId);
            }
        }
        lock.unlock();
        globalLock.readLock().unlock();
    }

    @Override
    public void globalLock() throws EntityLockerException {
        generalGlobalLock(lock -> {
            lock.lock();
            return true;
        });
    }

    @Override
    public boolean tryGlobalLock() throws EntityLockerException {
        return generalGlobalLock(Lock::tryLock);
    }

    @Override
    public void globalUnlock() throws EntityLockerException {
        if (!(Thread.currentThread() == globalLockThread)) {
            throw new EntityLockerException(incorrectUnlockingGlobalThread(Thread.currentThread()));
        }
        synchronized (this) {
            globalLockThread = null;
        }
        globalLock.writeLock().unlock();
    }

    @Override
    public int currentLocksCount() {
        return entityLockMap.size();
    }

    @Override
    public boolean isLockedByCurrentThread(T entityId) {
        return entityLockMap.containsKey(entityId) && entityLockMap.get(entityId).isHeldByCurrentThread();
    }

    @Override
    public boolean isLockedByAnotherThread(T entityId) {
        return entityLockMap.containsKey(entityId) && !entityLockMap.get(entityId).isHeldByCurrentThread();
    }

    private boolean generalLock(T entityId, Function<Lock, Boolean> callable)
            throws EntityLockerException {
        final Thread currentThread = Thread.currentThread();
        globalLock.readLock().lock();
        final ReentrantLock lock = entityLockMap.getOrDefault(entityId, new ReentrantLock());
        putToWait(currentThread, entityId, lock);
        final boolean lockingResult = callable.apply(lock);
        entityLockMap.putIfAbsent(entityId, lock);
        putFromWaitingToLocking(currentThread, entityId, lockingResult);
        return lockingResult;
    }

    private boolean generalGlobalLock(Function<Lock, Boolean> callable) throws EntityLockerException {
        synchronized (this) {
            final Thread currentThread = Thread.currentThread();
            if (!canThreadAcquireGlobalLock()) {
                throw new EntityLockerException(globalDeadlockCheckerMessage(currentThread));
            }
            checkGlobalDeadlock();
            waitingThreads.put(currentThread, null);
            globalLockThread = currentThread;
        }
        final boolean lockingResult = callable.apply(globalLock.writeLock());
        synchronized (this) {
            waitingThreads.remove(globalLockThread);
        }
        return lockingResult;
    }

    private synchronized void putToWait(Thread  currentThread, T entityId, ReentrantLock lock) throws EntityLockerException {
        if (lock.isLocked() && !lock.isHeldByCurrentThread()) {
            if (checkDeadlock(entityId)) {
                globalLock.readLock().unlock();
                throw new EntityLockerException(deadlockCheckMessage(currentThread, entityId));
            }
            waitingThreads.put(currentThread, entityId);
        }
    }

    private synchronized void putFromWaitingToLocking(Thread currentThread, T entityId, boolean ifLock) {
        waitingThreads.remove(currentThread);
        if (ifLock) {
            entitiesLockedBy.put(entityId, currentThread);
            threadsLocks.putIfAbsent(currentThread, new HashSet<>());
            threadsLocks.get(currentThread).add(entityId);
        }
    }

    private boolean checkDeadlock(final T entityId) {
        final Thread currentThread = Thread.currentThread();
        T currentEntityId = entityId;
        while (entitiesLockedBy.containsKey(currentEntityId)) {
            Thread lockingThread = entitiesLockedBy.get(currentEntityId);
            currentEntityId = waitingThreads.get(lockingThread);
            if (lockingThread == currentThread) {
                return true;
            }
        }
        return false;
    }

    private void checkGlobalDeadlock() throws EntityLockerException {
        final Thread currentThread = Thread.currentThread();
        for (Map.Entry<Thread, T> waitingPair : waitingThreads.entrySet()) {
            final Thread thread = entitiesLockedBy.get(waitingPair.getValue());
            if (thread == currentThread) {
                throw new EntityLockerException(globalDeadlockCheckerMessage(currentThread));
            }
        }
    }

    private boolean canThreadAcquireGlobalLock() {
        final Thread currentThread = Thread.currentThread();
        return !(globalLockThread != null && currentThread != globalLockThread &&
                threadsLocks.getOrDefault(currentThread, null) != null);
    }

    private String noLocksError(T entityId) {
        return "Entity " + entityId + " is not locked, cannot unlock";
    }

    private String incorrectUnlockingThread(T entityId, Thread thread) {
        return "Thread " + thread.getName() + " can't unlock entity " + entityId + " because lock belongs to another thread";
    }

    private String incorrectUnlockingGlobalThread(Thread thread) {
        return "Thread " + thread.getName() + " can't unlock global lock because lock belongs to another thread";
    }

    private String deadlockCheckMessage (final Thread failedThread, final T entityId) {
        return "Thread " + failedThread.getName() + " cannot acquire lock on " + entityId + " due to deadlock condition.";
    }

    private String globalDeadlockCheckerMessage (final Thread failedThread) {
        return "Thread " + failedThread.getName() + " cannot acquire global lock due to deadlock condition.";
    }
}

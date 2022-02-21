package shashok.concurrent;

import shashok.concurrent.exception.EntityLockerException;

import java.util.concurrent.TimeUnit;

public interface EntityLocker <T> {

    /**
     * Acquires the lock. Allows to designate which entity the caller works with.
     * Caller waits indefinitely.
     * @param entityId the entity ID of the entity which should be locked
     */
    void lock(T entityId) throws EntityLockerException;

    /**
     * Tries to acquire the lock on entity. Caller doesn't wait if the resource can't be locked on.
     * @param entityId the entity ID of the entity which should be locked
     * @return False if lock failed
     */
    boolean tryLock(T entityId) throws EntityLockerException;

    /**
     * Acquires the lock on entity. Caller waits for a designated time.
     * @param entityId the entity ID of the entity which should be locked
     * @param timeout time to wait for a lock
     */
    boolean timeoutLock(T entityId, long timeout, TimeUnit timeUnit) throws EntityLockerException;

    /**
     * Unlocks a resource.
     * @param entityId the entity ID of the entity which should be unlocked
     */
    void unlock(T entityId) throws EntityLockerException;

    /**
     * Acquires global lock.
     */
    void globalLock() throws EntityLockerException;

    /**
     * Tries to acquire global lock.
     * @return
     */
    boolean tryGlobalLock() throws EntityLockerException;

    /**
     * Tries to unlock a global lock.
     */
    void globalUnlock() throws EntityLockerException;

    /**
     * Shows how many there are locks, excluding the global lock.
     * @return number of locks
     */
    int currentLocksCount();

    /**
     * Checks if some entity is locked by this thread.
     * @param entityId entity ID to check
     * @return whether or not this thread locks entity
     */
    boolean isLockedByCurrentThread(T entityId);

    /**
     * Checks if some entity is locked by another thread.
     * @param entityId entity ID to check
     * @return whether or not another thread locks entity
     */
    boolean isLockedByAnotherThread(T entityId);
}

package com.github.hmdev.web;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

import org.junit.Test;

/**
 * GUI が Web 変換の鍵を、中止を見ながら待って取る（internal #22）。
 * 一瞬で取れる待ちは知らせない・中止で待つのをやめる・残った割り込みの印ではやめない
 */
public class WebAozoraConverterWebLockTest {

	/** ほかのスレッドに lock を持たせ、release が数えられるまで持ち続ける */
	private static Thread hold(ReentrantLock lock, CountDownLatch held, CountDownLatch release) {
		Thread t = new Thread(() -> {
			lock.lock();
			try {
				held.countDown();
				release.await();
			} catch (InterruptedException e) {
				Thread.currentThread().interrupt();
			} finally {
				lock.unlock();
			}
		});
		t.start();
		return t;
	}

	/** 空いていればすぐ取り、待ったことを知らせない */
	@Test(timeout = 10000)
	public void takesAFreeLockWithoutAMessage() {
		ReentrantLock lock = new ReentrantLock();
		AtomicInteger messages = new AtomicInteger();
		assertTrue(WebAozoraConverter.lockWaiting(lock, () -> false, 0, messages::incrementAndGet, messages::incrementAndGet));
		assertTrue(lock.isHeldByCurrentThread());
		lock.unlock();
		assertEquals(0, messages.get());
	}

	/** 持たれている間に中止されたら、取らずに false。待ち始めの知らせは出る */
	@Test(timeout = 10000)
	public void aCancelEndsTheWaitWithoutTheLock() throws Exception {
		ReentrantLock lock = new ReentrantLock();
		CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
		Thread holder = hold(lock, held, release);
		held.await();
		AtomicBoolean cancel = new AtomicBoolean();
		AtomicInteger start = new AtomicInteger(), end = new AtomicInteger();
		new Thread(() -> { try { Thread.sleep(400); } catch (InterruptedException e) { /* 試験 */ } cancel.set(true); }).start();
		long t0 = System.nanoTime();
		assertFalse(WebAozoraConverter.lockWaiting(lock, cancel::get, 100, start::incrementAndGet, end::incrementAndGet));
		assertTrue("中止の後もしばらく待った", System.nanoTime() - t0 < TimeUnit.SECONDS.toNanos(2));
		assertFalse(lock.isHeldByCurrentThread());
		assertEquals(1, start.get());
		assertEquals(0, end.get());
		release.countDown();
		holder.join();
	}

	/** 待った後に取れたら、待ち始めと待ち終わりを 1 回ずつ知らせる */
	@Test(timeout = 10000)
	public void announcesTheStartAndTheEndOfALongWait() throws Exception {
		ReentrantLock lock = new ReentrantLock();
		CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
		Thread holder = hold(lock, held, release);
		held.await();
		new Thread(() -> { try { Thread.sleep(500); } catch (InterruptedException e) { /* 試験 */ } release.countDown(); }).start();
		AtomicInteger start = new AtomicInteger(), end = new AtomicInteger();
		assertTrue(WebAozoraConverter.lockWaiting(lock, () -> false, 100, start::incrementAndGet, end::incrementAndGet));
		assertTrue(lock.isHeldByCurrentThread());
		lock.unlock();
		assertEquals(1, start.get());
		assertEquals(1, end.get());
		holder.join();
	}

	/** 知らせるまでの時間より早く取れた待ち（本棚の名前の変更など）は知らせない */
	@Test(timeout = 10000)
	public void aShortWaitIsNotAnnounced() throws Exception {
		ReentrantLock lock = new ReentrantLock();
		CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
		Thread holder = hold(lock, held, release);
		held.await();
		//試し直しの 1 回目（250ms）より後に空く
		new Thread(() -> { try { Thread.sleep(450); } catch (InterruptedException e) { /* 試験 */ } release.countDown(); }).start();
		AtomicInteger messages = new AtomicInteger();
		assertTrue(WebAozoraConverter.lockWaiting(lock, () -> false, 3000, messages::incrementAndGet, messages::incrementAndGet));
		lock.unlock();
		assertEquals(0, messages.get());
		holder.join();
	}

	/** 前の仕事が残した割り込みの印ではやめずに待ち、取れたら印を戻す */
	@Test(timeout = 10000)
	public void aLeftoverInterruptDoesNotEndTheWait() throws Exception {
		ReentrantLock lock = new ReentrantLock();
		CountDownLatch held = new CountDownLatch(1), release = new CountDownLatch(1);
		Thread holder = hold(lock, held, release);
		held.await();
		new Thread(() -> { try { Thread.sleep(400); } catch (InterruptedException e) { /* 試験 */ } release.countDown(); }).start();
		Thread.currentThread().interrupt();
		boolean got = WebAozoraConverter.lockWaiting(lock, () -> false, 10_000, () -> {}, () -> {});
		boolean flag = Thread.interrupted();
		assertTrue("割り込みの印で待つのをやめた", got);
		assertTrue("割り込みの印を戻していない", flag);
		lock.unlock();
		holder.join();
	}
}

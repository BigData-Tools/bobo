/**
 * 
 */
package com.browseengine.bobo.util;

import java.lang.ref.WeakReference;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.log4j.Logger;

/**
 * @author "Xiaoyang Gu<xgu@linkedin.com>"
 *
 */
public class MemoryManager<T>
{
  private static final Logger log = Logger.getLogger(MemoryManager.class.getName());
  private static final long SWEEP_INTERVAL = 60000; // 1min
  private static final ReentrantLock _sweepLock = new ReentrantLock();

  private static volatile long _nextSweepTime = System.currentTimeMillis();
  private static volatile int _nextSweepIndex = 0;


  private final ConcurrentHashMap<Integer, ConcurrentLinkedQueue<WeakReference<T>>> _sizeMap = new ConcurrentHashMap<Integer, ConcurrentLinkedQueue<WeakReference<T>>>();
  private Initializer<T> initializer;
  public MemoryManager(Initializer<T> initializer)
  {
    this.initializer = initializer;
  }

  /**
   * @return an initialized instance of type T.
   */
  public T get(int size)
  {
    ConcurrentLinkedQueue<WeakReference<T>> queue = _sizeMap.get(size);
    if (queue==null)
    {
      queue =  new ConcurrentLinkedQueue<WeakReference<T>>();
      _sizeMap.putIfAbsent(size, queue);
      queue = _sizeMap.get(size);
    }
    while(true)
    {
      WeakReference<T> ref = (WeakReference<T>) queue.poll();
      if(ref != null)
      {
        T buf = ref.get();
        if(buf != null)
        {
          initializer.init(buf);
//          log.info("array hit " + size);
          return buf;
        }
      }
      else
      {
//        log.info("array miss " + size);
        return initializer.newInstance(size);
      }
    }
  }

  /**
   * return the instance to the manager after use
   * @param buf
   */
  public void release(T buf)
  {
    if(buf != null)
    {
      ConcurrentLinkedQueue<WeakReference<T>> queue = _sizeMap.get(initializer.size(buf));
      // buf is wrapped in WeakReference. this allows GC to reclaim the buffer memory
      queue.offer(new WeakReference<T>(buf));
    }
  }

  public static interface Initializer<E>
  {
    public E newInstance(int size);
    public int size(E buf);
    public void init(E buf);
  }
}

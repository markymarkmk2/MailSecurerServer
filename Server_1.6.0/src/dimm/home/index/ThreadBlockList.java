/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.index;

import java.io.IOException;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import org.apache.lucene.index.CorruptIndexException;

/**
 *
 * @author mw
 */
public abstract class ThreadBlockList  implements Runnable
{
    Thread[] thread_array;
    Thread wathdog_thread;
    BlockingQueue<IndexJobEntry> queue;
    boolean isRunnerRunning;

    boolean keepRunning = true;
    long sleepMilisecondOnEmpty = 100;

    public ThreadBlockList( int cnt)
    {
        queue = new ArrayBlockingQueue<IndexJobEntry>(cnt);

        thread_array = new Thread[cnt];

        for (int i = 0; i < cnt; i++)
        {
            thread_array[i] = start();
        }

        wathdog_thread = new Thread(this, "IndexWatchdog" );
        wathdog_thread.start();
    }

    abstract Thread start();

    private void stop()
    {
        this.keepRunning = false;
        try
        {
            while (isRunnerRunning)
            {
                //using the same sleep duration as writer uses
                Thread.sleep(sleepMilisecondOnEmpty);
            }
        }
        catch (InterruptedException e)
        {
            e.printStackTrace();
        }
    }
    void execute( IndexJobEntry ije ) throws InterruptedException
    {
        //System.out.println("QS free: " + queue.remainingCapacity());
        queue.put(ije);
    }



    public void close() throws CorruptIndexException, IOException
    {
        stop();
    }

    public int get_write_queue_size()
    {
        return queue.size();
    }

    public int get_queue_entries()
    {
        return get_write_queue_size() /*+ get_extract_queue_size()*/;
    }

    @Override
    public void run()
    {
        while (this.keepRunning)
        {
            for (int i = 0; i < thread_array.length; i++)
            {
                Thread thread = thread_array[i];

                try
                {
                    thread.join(100);
                }
                catch (InterruptedException interruptedException)
                {
                }
                if (thread.isInterrupted() || !thread.isAlive())
                {
                    thread_array[i] = null;

                    // REVIVE
                    thread_array[i] = start();
                }
            }
            try
            {
                Thread.sleep(500);
            }
            catch (InterruptedException interruptedException)
            {
            }
        }
    }

    int get_busy_threads()
    {
        return queue.size() - queue.remainingCapacity();
    }
}
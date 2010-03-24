/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */
package dimm.home.index;

import dimm.home.mailarchiv.Utilities.LogManager;
import java.io.IOException;

import java.util.concurrent.ArrayBlockingQueue;

import java.util.concurrent.BlockingQueue;


import org.apache.lucene.index.CorruptIndexException;

class WriteRunner implements Runnable
{

    final AsyncIndexWriter aiw;

    public WriteRunner( AsyncIndexWriter aiw )
    {
        this.aiw = aiw;
    }

    @Override
    public void run()
    {
        while (aiw.keepRunning || !aiw.index_write_queue.isEmpty())
        {
            IndexJobEntry ije = aiw.index_write_queue.poll();
            try
            {
                if (ije != null)
                {
                    ije.handle_post_index();
                    
                }
                else
                {
                    /*
                     * Nothing in queue so lets wait
                     */
                    Thread.sleep(aiw.sleepMilisecondOnEmpty);

/*
                    try
                    {
                        // LOOK FOR DEAD THREADS  (OOM-EXCEPTIONS DURING EXTRACT)
                        IndexJobEntry xije = aiw.index_extract_queue.peek();
                        if (xije != null)
                        {
                            Thread thread = xije.finished;
                            xije.ixm.get_index_thread_pool().

                            if (thread != null && (!thread.isAlive() || thread.isInterrupted()))
                            {
                                Thread.sleep(aiw.sleepMilisecondOnEmpty);
                                if (!thread.isAlive() || thread.isInterrupted())
                                {
                                    LogManager.err_log("Reviving dead extract thread");
                                    thread = new Thread(xije, "RevivedExtractorRunner");
                                    try
                                    {
                                        xije.thread.join(100);
                                    }
                                    catch (InterruptedException interruptedException)
                                    {
                                    }
                                    xije.thread = thread;
                                    thread.start();
                                }
                            }
                        }
                    }
                    catch (Exception e)
                    {
                        e.printStackTrace();
                    }*/
                }

            }
            catch (Exception e)
            {
                LogManager.err_log("Error in index write runner", e );
                e.printStackTrace();
                
            }
        }

        aiw.isWRunning = false;
    }
}

/**

 * @author swiki swiki

 *

 */
public class AsyncIndexWriter
{

    /*
     * A blocking queue of document to facilitate asynchronous writing.
     */
    BlockingQueue<IndexJobEntry> index_write_queue;
   //BlockingQueue<IndexJobEntry> index_extract_queue;
    /*
     * Thread which makes writing asynchronous
     */
    private Thread writerThread;
    /*
     * We need to set this to false if the document addition is completed. This
     * will not immediately stop the writing as there could be some documents in
     * the queue. It completes once all documents are written and the queue is
     * empty.
     */
    boolean keepRunning = true;

    /*
     * This flag is set to false once writer is done with the queue data
     * writing.
     */
    /*
     * Duration in miliseconds for which the writer should sleep when it finds
     * the queue empty and job is still not completed
     */
    long sleepMilisecondOnEmpty = 100;
    boolean isWRunning;

    /**
     * This method should be used to add documents to index queue. If the queue
     * is full it will wait for the queue to be available.
     *
     * @param doc
     * @throws InterruptedException
     */
    public void add_to_write_queue( IndexJobEntry ije ) throws InterruptedException
    {
            index_write_queue.put(ije);
    }
/*
    public void add_to_extract_queue( IndexJobEntry ije ) throws InterruptedException
    {
            index_extract_queue.put(ije);
    }

    void remove_from_extract_queue( IndexJobEntry ije )
    {
            index_extract_queue.remove(ije);
    }*/

    public void startWriting()
    {
        WriteRunner wr = new WriteRunner(this);
        writerThread = new Thread(wr, "WriteRunner");
        writerThread.start();
    }

    /**
     * Constructor with indexwriter and queue size as input. It Uses
     * ArrayBlockingQueue with size queueSize and sleepMilisecondOnEmpty is
     * 100ms
     *
     * @param w
     * @param queueSize
     */
    public AsyncIndexWriter( int queueSize )
    {
        this(queueSize, 100);
    }

    

    /**
     * A implementation of BlockingQueue can be used
     *
     * @param w
     * @param queueSize
     * @param sleepMilisecondOnEmpty
     */
    public AsyncIndexWriter( int queueSize,
            long sleepMilisecondOnEmpty )
    {
        index_write_queue = new ArrayBlockingQueue<IndexJobEntry>(100);
        //index_extract_queue = new ArrayBlockingQueue<IndexJobEntry>(queueSize);
        this.sleepMilisecondOnEmpty = sleepMilisecondOnEmpty;
        startWriting();
    }
    /*
     * (non-Javadoc)
     *
     * @see java.lang.Runnable#run()
     */

    /**

     * Stop the thread gracefully, wait until its done writing.

     */
    private void stopWriting()
    {
        this.keepRunning = false;
        try
        {
            while (isWRunning /*&& index_extract_queue.size() > 0*/)
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

    public void optimize() throws CorruptIndexException, IOException
    {
    }

    public void close() throws CorruptIndexException, IOException
    {
        stopWriting();
    }

    public int get_write_queue_size()
    {
        return index_write_queue.size();
    }
/*
    public int get_extract_queue_size()
    {
        return index_extract_queue.size();
    }*/

    public int get_queue_entries()
    {
        return get_write_queue_size() /*+ get_extract_queue_size()*/;
    }
}

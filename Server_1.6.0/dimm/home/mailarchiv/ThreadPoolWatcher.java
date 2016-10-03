/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv;

import dimm.home.index.OfferBlockingQueue;
import dimm.home.mailarchiv.Utilities.LogManager;
import java.util.ArrayList;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;



class NamedThreadFactory implements ThreadFactory {

    String name;
    ThreadPoolExecutor pool;

    public NamedThreadFactory( String name )
    {
        this.name = name;
    }
    

    @Override
    public Thread newThread( Runnable r )
    {
        String thr_name = name;

        if (pool != null)
        {
            thr_name = name + "-" + pool.getPoolSize();
        }
        Thread thr = new Thread(r, thr_name);
        return thr;
    }

    void set_pool( ThreadPoolExecutor ret )
    {
        pool = ret;
    }

    @Override
    public String toString()
    {
        return name;
    }

}
class NamedThreadPoolExecutor extends ThreadPoolExecutor implements RejectedExecutionHandler
{
    String name;

    public NamedThreadPoolExecutor( String _name, int queue_size, int core_threads, int max_threads, int time, TimeUnit unit)
    {
        super( core_threads, max_threads, time, unit, new OfferBlockingQueue<Runnable>(queue_size), new NamedThreadFactory( _name ));
        NamedThreadFactory thr_fact = (NamedThreadFactory) getThreadFactory();
        thr_fact.set_pool( this );

        setRejectedExecutionHandler(this);
    }

    @Override
    public void rejectedExecution( Runnable r, ThreadPoolExecutor executor )
    {
        LogManager.msg_index(LogManager.LVL_WARN, "Detected reject for pool " + name);
        r.run();
    }


}
    /**
 *
 * @author mw
 */
public class ThreadPoolWatcher
{
    ArrayList<NamedThreadPoolExecutor> pool_list;
    String pool_name;

    public ThreadPoolWatcher(String _name)
    {
        pool_name = _name;
        pool_list = new ArrayList<NamedThreadPoolExecutor>();
    }


    public boolean shutdown_thread_pools( int ms)
    {
        LogManager.msg_index(LogManager.LVL_DEBUG, "Shutting down Thread Pool");

        for (int i = 0; i < pool_list.size(); i++)
        {
            ThreadPoolExecutor pool = pool_list.get(i);
            pool.shutdown();
        }

        boolean pool_finished = true;

        int cycle = 1000;
        while(ms > 0)
        {
            pool_finished = true;
            for (int i = 0; i < pool_list.size(); i++)
            {
                NamedThreadPoolExecutor pool = pool_list.get(i);
                try
                {
                    if (!pool.awaitTermination(10, TimeUnit.MILLISECONDS))
                    {
                        pool_finished = false;
                    }
                }
                catch (InterruptedException interruptedException)
                {
                    pool_finished = false;
                    LogManager.msg_system(LogManager.LVL_WARN, "awaitTermination Thread Pool " + pool.toString() + " aborted", interruptedException);
                }
            }
            if (pool_finished == true)
                break;
            LogicControl.sleep(cycle);
            ms -= cycle;
        }


        if (!pool_finished)
            LogManager.msg_system(LogManager.LVL_WARN, "Shutting down Thread Pool failed");
        else
            LogManager.msg_system(LogManager.LVL_DEBUG, "Shutting down Thread Pool succeeded");

        return pool_finished;
    }

    public boolean abort_thread_pool( ThreadPoolExecutor pool)
    {
        String name = "";
        if (pool instanceof NamedThreadPoolExecutor)
        {
            name = " " + ((NamedThreadPoolExecutor)pool).toString();
        }
        LogManager.msg_index(LogManager.LVL_DEBUG, "Aborting Thread Pool" + name);

        pool.shutdownNow();

        boolean pool_finished = true;
        try
        {
            if (!pool.awaitTermination(1000, TimeUnit.MILLISECONDS))
            {
                pool_finished = false;
            }
        }
        catch (InterruptedException interruptedException)
        {
            pool_finished = false;
            LogManager.msg_system(LogManager.LVL_WARN, "awaitTermination Thread Pool aborted"  + name, interruptedException);
        }


        if (!pool_finished)
            LogManager.msg_system(LogManager.LVL_DEBUG, "Aborting Thread Pool failed" + name);
        else
            LogManager.msg_system(LogManager.LVL_DEBUG, "Aborting Thread Pool succeeded" + name);

        return pool_finished;
    }




    public boolean shutdown_thread_pool( ThreadPoolExecutor pool, int ms)
    {
        String name = "";
        if (pool instanceof NamedThreadPoolExecutor)
        {
            name = " " + ((NamedThreadPoolExecutor)pool).toString();
        }
        LogManager.msg_index(LogManager.LVL_DEBUG, "Shutting down Thread Pool" + name);

        pool.shutdown();

        boolean pool_finished = true;
        try
        {
            if (!pool.awaitTermination(ms, TimeUnit.MILLISECONDS))
            {
                pool_finished = false;
            }
        }
        catch (InterruptedException interruptedException)
        {
            pool_finished = false;
            LogManager.msg_system(LogManager.LVL_WARN, "awaitTermination Thread Pool aborted"  + name, interruptedException);
        }


        if (!pool_finished)
            LogManager.msg_system(LogManager.LVL_DEBUG, "Shutting down Thread Pool failed" + name);
        else
            LogManager.msg_system(LogManager.LVL_DEBUG, "Shutting down Thread Pool succeeded" + name);

        return pool_finished;
    }


    public ThreadPoolExecutor create_blocking_thread_pool( String name, int threads, int queue_size )
    {
        //OfferBlockingQueue<Runnable> run_queue = new OfferBlockingQueue<Runnable>(queue_size);

       // NamedThreadFactory thr_fact = new NamedThreadFactory(pool_name + "-" + name);

        //NamedThreadPoolExecutor ret = new ThreadPoolExecutor(threads, threads, 10, TimeUnit.MINUTES, run_queue, thr_fact);
        NamedThreadPoolExecutor ret = new NamedThreadPoolExecutor( name, queue_size, threads, threads, 60, TimeUnit.MINUTES);
        
        pool_list.add( ret );

        return ret;
    }

}

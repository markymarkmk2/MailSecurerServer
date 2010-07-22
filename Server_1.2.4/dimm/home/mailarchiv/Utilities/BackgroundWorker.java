/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package dimm.home.mailarchiv.Utilities;

import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 *
 * @author mw
 */
public abstract class BackgroundWorker implements Callable<Object>
{
    private Object value;  // see getValue(), setValue()
    Future<Object> result;
    
    static ExecutorService service;

    /**
     * Class to maintain reference to current worker thread
     * under separate synchronization control.
     */
    

   

    /**
     * Get the value produced by the worker thread, or null if it
     * hasn't been constructed yet.
     */
    protected synchronized Object getValue()
    {
        return value;
    }

    /**
     * Set the value produced by worker thread
     */
    private synchronized void setValue(Object x)
    {
        value = x;
    }

    /**
     * Compute the value to be returned by the <code>get</code> method.
     */
    public abstract Object construct();

    /**
     * Called on the event dispatching thread (not on the worker thread)
     * after the <code>construct</code> method has returned.
     */
    
    public boolean finished()
    {
        return result.isDone();
    }

    /**
     * A new method that interrupts the worker thread.  Call this method
     * to force the worker to stop what it's doing.
     */
    public void interrupt()
    {
        result.cancel(true);
    }


    /**
     * Return the value created by the <code>construct</code> method.
     * Returns null if either the constructing thread or the current
     * thread was interrupted before a value was produced.
     *
     * @return the value created by the <code>construct</code> method
     */
    public Object get()
    {
        try
        {
            return result.get();
        }
        catch (InterruptedException interruptedException)
        {
            return null;
        }
        catch (ExecutionException executionException)
        {
            return null;
        }
    }

    public void join(int ms)
    {
        try
        {
            result.get(ms, TimeUnit.MILLISECONDS);
        }
        catch (InterruptedException interruptedException)
        {
        }
        catch (ExecutionException executionException)
        {
        }
        catch (TimeoutException timeoutException)
        {
        }
    }
    public boolean isAlive()
    {
        return !result.isDone();
    }

    class NamedThreadFactory implements  ThreadFactory
{

    String name;


    public NamedThreadFactory( String name )
    {
        this.name = name;
    }


    @Override
    public Thread newThread( Runnable r )
    {
        String thr_name = name;

        Thread thr = new Thread(r, thr_name);
        return thr;
    }

}

    /**
     * Start a thread that will call the <code>construct</code> method
     * and then exit.
     */
    public BackgroundWorker(String name)
    {
        if (service == null)
            service = Executors.newCachedThreadPool(new NamedThreadFactory("BackgroundWorker") );

/*
        final Runnable doFinished = new Runnable()
        {
            @Override
            public void run()
            { finished(); }
        };
*/
/*        Runnable doConstruct = new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    setValue(construct());
                }
                finally
                {
                    threadVar.clear();
                }

                SwingUtilities.invokeLater(doFinished);
            }
        };

        Thread t = new Thread(doConstruct, name);
        threadVar = new ThreadVar(t);
 * */
    }

    /**
     * Start the worker thread.
     */
    public void start()
    {
        result = service.submit( this );

        
        /*new Callable<Object>()
        {

            @Override
            public Object call() throws Exception
            {
                setValue(construct());
                return value;
            }
        });*/

/*        Thread t = threadVar.get();
        if (t != null)
        {
            t.start();
        }*/
    }
    @Override
    public Object call() throws Exception
    {
        setValue(construct());
        return value;
    }
}

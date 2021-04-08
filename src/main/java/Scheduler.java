import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Semaphore;

/**
 * Scheduler Class - Implementing FIFO Multi-Core Scheduling
 */
public class Scheduler implements Runnable{
    //Number of Cores
    private int coreCount;

    //Lists Containing All Processes and Ready Ones
    private List<Process> processWaitingQ, processReadyQ;

    //Process Threads List
    private List<Thread> threadQueue;

    //Semaphore Representing Number of Cores Available
    public static Semaphore coreCountSem;

    //Parametrized Scheduler Constructor
    Scheduler(ArrayList<Process> allProcesses, int cores){
        coreCount = cores;
        processWaitingQ = allProcesses;
        processReadyQ = new ArrayList<>();
        threadQueue = new ArrayList<>();
        coreCountSem = new Semaphore(cores);
    }

    /**
     * Runs Scheduler Thread Until Waiting and Ready Queue Are Empty
     * - Determine Whether Process is Ready or Waiting and Move them to their Corresponding Queues
     * - Schedule Ready Processes using Semaphore Representing the Number of Cores Available
     */
    @Override
    public void run() {
        main.log.info("Scheduler Started!");

        //Main Scheduler Execution
        while(!processWaitingQ.isEmpty() || !processReadyQ.isEmpty()) {
            readyCheck();
            executingMethod();

            //Sleep Thread
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                main.log.error(e.getMessage());
            }
        }

        //Join All Threads to Terminate Scheduling Properly
        for(Thread thread: threadQueue) {
            try {
                thread.join();
            } catch (InterruptedException e) {
                main.log.error(e.getMessage());
            }
        }

        main.log.info("Scheduler Stopped!");
    }

    /**
     * Moves Ready Process to Ready Queue and Removes them from Waiting Queue
     * - Checks if Process Has Arrived/Ready
     */
    public void readyCheck() {
        for (int i = 0; i < processWaitingQ.size(); i++) {
            Process process = processWaitingQ.get(i);
            if (process.getStart() <= Clock.INSTANCE.getTime()/1000) {
                processReadyQ.add(process);
                processWaitingQ.remove(process);

                //Reset Queue
                i = -1;
            }
        }
    }

    /**
     * Scheduling in FIFO Processes on Multiple Cores
     */
    public void executingMethod() {
        for(int i = 0; i < processReadyQ.size(); i++) {
            //Start Process Thread
            startCheck();

            //Sleep Thread
            try {
                Thread.sleep(10);
            } catch (InterruptedException e) {
                main.log.error(e.getMessage());
            }
        }
    }

    /**
     * Create, Add, and Start Threads
     */
    public void startCheck() {
        //Create and Add Thread to Thread Queue
        Thread processT = new Thread(processReadyQ.remove(0));
        threadQueue.add(processT);

        //Start Thread When Semaphore Has Permit
        try {
            coreCountSem.acquire();
            processT.start();
        } catch (InterruptedException e) {
            main.log.error(e.getMessage());
        }
    }
}
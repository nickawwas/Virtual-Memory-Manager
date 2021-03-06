import java.util.LinkedList;
import java.util.concurrent.Semaphore;

public class Memory implements Runnable{
    // Memory Size
    private final int memorySize;

    //Main Memory and Large Disk
    private LinkedList<Page> mainMemory;
    private Disk largeDisk;

    //Memory Management Unit (MMU) Attributes
    // Current and Start Clock Times
    private int currentClock, startClock;
    // Current Command to Run
    private Command currentCommand;
    // Current Process Requesting Utility from Memory Manager
    private Process currentProcess;
    // Semaphore to Ensure Only 1 Process Uses Memory at a Time
    private Semaphore lockSem;
    // Flags to Determine if Memory Manager Can Run a Command or Can Be Terminated
    private boolean terminate, commandAvailable;

    /**
     * Parameterized Constructor - Initialize Main Memory and Large Disk Given Memory Size
     */
    public Memory(int size) {
        memorySize = size;
        largeDisk = new Disk();
        mainMemory = new LinkedList<>();

        lockSem = new Semaphore(1);

        currentProcess = null;
        currentCommand = null;
        currentClock = -1;

        terminate = false;
        commandAvailable = false;
    }

    /**
     * API of the Memory Class
     * Store: Stores an ID with its corresponding value in the main memory or virtual memory
     *        If a page with the same Id already exists, the old one will be deleted and replaced by the new one
     * @param varId
     * @param varValue
     */
    public void store(String varId, int varValue) {
        //Update LRU Main Memory -> Move to Back by Removing Then Adding Back
        int location = searchMemory(varId);
        if(location != -1) {
            //Remove From Main Memory
            removeMemoryVariable(location);
        }
        location = searchDisk(varId);
        if (location != -1) {
            //Remove From Large Disk
            removeDiskVariable(varId);
        }

        Page v = new Page(varId, varValue);
        //Add Main Memory if Space is Available
        if (!isFull())
            addMemoryVariable(v);
        //Add to Large Disk Space Otherwise
        else
            addDiskVariable(v);
    }

    /**
     * API of the Memory Class
     * Release: Releases an ID with its corresponding value from the main memory or virtual memory
     * @param varId
     * @return returns the ID of the removed item if successful, -1 if ID not found
     */
    public int release(String varId) {
        //Attempt 1: Search Id in Main Memory
        int location = searchMemory(varId);
        if(location != -1) {
            //Remove From Main Memory
            removeMemoryVariable(location);

            //Return the Id of the removed variable (page)
            return Integer.parseInt(varId);
        }

        //Attempt 2: Search Id in Large Disk
        location = searchDisk(varId);
        if(location != -1) {
            //Remove From Large Disk
            removeDiskVariable(varId);

            //Return the Id of the removed variable (page)
            return Integer.parseInt(varId);
        }

        //Return - No Id Found
        return -1;
    }

    /**
     * API of the Memory Class
     * Lookup: Looks up an ID's value from the main memory or virtual memory
     *         If the ID is found in the VM, it is swapped with a value in MM using LRU
     * @param varId
     * @return returns the value of the ID if successful, -1 if not found
     */
    public int lookup(String varId) {
        int location = searchMemory(varId);

        //Search Id in Main Memory
        if(location != -1) {
            // Changing List Order to Accommodate for LRU
            addMemoryVariable(mainMemory.remove(location));
            return mainMemory.getLast().getValue();
        }

        //Search Id in Disk Space for Value
        location = searchDisk(varId);

        if(location != -1) {
            //Found in Large Disk! - Page Fault Occurs
            int val = location;

            //Release Id From Virtual Memory (Large Disk)
            removeDiskVariable(varId);

            //Move Variable Into Main Memory
            if(isFull()) {
                //Full! - Swap using Least Recently Used (LRU) Page
                String swappedId = mainMemory.getFirst().getId();

                //Add the least accessed Page in Main Memory to the Large Disk
                addDiskVariable(mainMemory.getFirst());

                //Remove the least accessed Page from Main Memory
                mainMemory.removeFirst();

                String message = ", Memory Manager, SWAP: Variable " + varId + " with Variable " + swappedId;
                Clock.INSTANCE.logEvent("Clock: " + currentClock + message);
            }

            //Add Variable to Main Memory
            addMemoryVariable(new Page(varId, val));
            return val;
        }

        return location;
    }

    /**
     * Check If Main Memory is Full
     * @return returns true if Full, false if not
     */
    public Boolean isFull() {
        return memorySize <= mainMemory.size();
    }

    /**
     * Add Variable to the end of the List (most recently accessed) After Checking isFull
     * @param var
     */
    public void addMemoryVariable(Page var) {
        mainMemory.addLast(var);
    }

    /**
     * Add Variable to the Disk (vm.txt)
     * @param var
     */
    public void addDiskVariable(Page var) {
        try {
            largeDisk.writeDisk(var.getId(), var.getValue());
        } catch (Throwable e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Search for Variable in Main Memory
     * @param id
     * @return returns Variable's index if successful, -1 if not found
     */
    public int searchMemory(String id) {
        //Check if Variable Id Matches Searched Id
        for(Page page: mainMemory)
            if (page.getId().equals(id))
                return mainMemory.indexOf(page);

        //Not Found
        return -1;
    }

    /**
     * Search for Variable in Large Disk
     * @param id
     * @return returns Variable's index if successful, -1 if not found
     */
    public int searchDisk(String id) {
        int val = -1;

        try {
            val = largeDisk.readDisk(id);
        } catch (Throwable e) {
            System.out.println(e.getMessage());
        }

        return val;
    }

    /**
     * Remove a Page/Variable from Main Memory given Index
     * @param index
     */
    public void removeMemoryVariable(int index) {
        mainMemory.remove(index);
    }

    /**
     * Remove a Page/Variable from Disk Memory given Index
     * @param id
     */
    public void removeDiskVariable(String id) {
        try{
            largeDisk.removeDisk(id);
        } catch (Throwable e) {
            System.out.println(e.getMessage());
        }
    }

    /**
     * Terminate the Memory Thread in Main
     */
    public void setStatus(boolean mmuDone) { terminate = mmuDone; }

    /**
     * Prints Main Memory Content to Check Functionality
     * @param m
     */
    public void printMem(String m) {
        for(Page page: mainMemory)
            Clock.INSTANCE.logEvent(m + page.toString());
    }

    /**
     * Modifies Common Variable to Run Memory Manager
     * - Using Shared Memory for IPC between Process Runnable and Memory Manager
     * @param command
     * @param currentP
     * @param clockCurrent
     */
    public void runCommands(Command command, Process currentP , int clockCurrent, int clockStart) {
        // Allow Only ONE Thread to Modify Memory at a Time!
        // Semaphore Used to Deal with Reader-Writer Problem
        try {
            lockSem.acquire();
        } catch(InterruptedException e){
            main.log.error(e.getMessage());
        }

        currentCommand = command;
        currentProcess = currentP;
        currentClock = clockCurrent;
        startClock = clockStart;
        commandAvailable = true;
    }

    @Override
    public void run() {
        main.log.info("Memory Started!");

        while(!terminate) {
            //Run Command Once Available, Else Sleep Thread
            if (commandAvailable) {
                switch (currentCommand.getCommand()) {
                    case "Release":
                        int r = release(currentCommand.getPageId());
                        Clock.INSTANCE.logEvent("Clock: " + currentClock + ", " + "Process " + currentProcess.getId() + ", Release: Variable " + currentCommand.getPageId());
                        break;
                    case "Lookup":
                        int l = lookup(currentCommand.getPageId());
                        Clock.INSTANCE.logEvent("Clock: " + currentClock + ", " + "Process " + currentProcess.getId() + ", Lookup: Variable " + currentCommand.getPageId() + ", Value: " + l);
                        break;
                    case "Store":
                        store(currentCommand.getPageId(), currentCommand.getPageValue());
                        Clock.INSTANCE.logEvent("Clock: " + currentClock + ", " + "Process " + currentProcess.getId() + ", Store: Variable " + currentCommand.getPageId() + ", Value: " + currentCommand.getPageValue());
                        break;
                    default:
                        Clock.INSTANCE.logEvent("Invalid Command");
                }

                //Simulate API Call
                commandSleeper();

                //Notify Process Thread
                synchronized (currentProcess) {
                    currentProcess.notify();
                }

                //Command Completed, None Available
                commandAvailable = false;

                //Release Lock on Memory Access
                lockSem.release();
            }

            //Sleep Process to Give Time to Respond to State Change
            try {
                Thread.sleep(5);
            } catch (Exception e) {
                main.log.error(e.getMessage());
            }
        }

        main.log.info("Memory Stopped!");
    }

    /**
     * Simulate API Call for Command
     */
    public void commandSleeper() {
        //Update Current Clock
        currentClock = Clock.INSTANCE.getTime();

        // Get Random Duration For Command Execution
        int commandDuration = (int) (Math.random() * 1000) + 1;

        // Fix Command Duration - Multiple of 10 To Match Clock & Use Up to Remaining Process Time
        commandDuration = (int) Math.floor(commandDuration/10.0) * 10;
        commandDuration = Math.min((1000 * currentProcess.getDuration()) - currentClock + startClock, commandDuration);

        // Ignore if Command API Call Takes No Time
        if (commandDuration <= 0 ) return;

        //Simulate Time for API Call
        int commandStart = Clock.INSTANCE.getTime();
        while (currentClock - commandStart < commandDuration) {
            try {
                Thread.sleep(2);
            } catch (Exception e) {
                main.log.error(e.getMessage());
            }

            currentClock = Clock.INSTANCE.getTime();
        }
    }
}

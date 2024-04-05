import java.util.ArrayList;
import java.util.Collections;
import java.util.Random;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.locks.ReentrantLock;


class Present
{
    int tag;
    AtomicReference<Present> next;

    Present(int tag) 
    {
        this.tag = tag;
        this.next = new AtomicReference<>(null);
    }
}

class ConcurrentLinkedList
{
    private AtomicReference<Present> head;
    private ReentrantLock lock;

    ConcurrentLinkedList()
    {
        head = new AtomicReference<>(null);
        lock = new ReentrantLock();
    }

    void add(Present present)
    {
        while (true)
        {
            Present pred = null;
            Present curr = head.get();

            // traverse the list to proper spot
            while (curr != null && curr.tag < present.tag)
            {
                pred = curr;
                curr = curr.next.get();
            }

            lock.lock();
            try {
                if (pred == null)
                {
                    // set the head, atomically update the head ref
                    present.next.set(head.get());
                    if (head.compareAndSet(head.get(), present)) { return; }

                }
                else
                {
                    // set the pred's next ref, atomically update it
                    present.next.set(pred.next.get());
                    if (pred.next.compareAndSet(pred.next.get(), present)) { return; }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    Present remove(int tag)
    {
        while (true) 
        {
            Present pred = null;
            Present curr = head.get();

            if (isEmpty()) { return null; }

            // traverse the list to find the tag
            while (curr != null && curr.tag < tag)
            {
                pred = curr;
                curr = curr.next.get();
            }

            lock.lock();
            try {
                if (curr == null || curr.tag != tag) { return null; } // present DNE
                
                if (pred == null) 
                {
                    // update the head (pred is null), return the removed element (curr)
                    if (head.compareAndSet(curr, curr.next.get())) { return curr; }
                }
                else 
                {
                    // update predecessor's new ref, return the removed
                    if (pred.next.compareAndSet(curr, curr.next.get())) { return curr; }
                }
            } finally {
                lock.unlock();
            }
        }
    }

    Present removeFirstPresent()
    {
        while (true)
        {
            if (isEmpty())
            {
                return null;
            }

            Present curr = head.get();
            lock.lock();
            try {
                // set the head's ref as the new head
                if (head.compareAndSet(curr, curr.next.get())) { return curr; };
            } finally {
                lock.unlock();
            }
        }
    }

    boolean contains(int tag)
    {
        Present curr = head.get();
        while (curr != null && curr.tag < tag)
        {
            curr = curr.next.get();
        }

        return curr != null && curr.tag == tag;
    }

    boolean isEmpty()
    {
        return head.get() == null;
    }
}

class Servant extends Thread
{
    private ConcurrentLinkedList list;
    private ArrayList<Present> bagOfPresents;
    private int numOfStartingPresents;
    protected AtomicBoolean checkForPresent;
    private Random random;
    private boolean printSteps;

    Servant(ConcurrentLinkedList list, ArrayList<Present> bagOfPresents, boolean printSteps)
    {
        this.list = list;
        this.bagOfPresents = bagOfPresents;
        this.numOfStartingPresents = bagOfPresents.size();
        this.checkForPresent = new AtomicBoolean(false);
        this.random = new Random();
        this.printSteps = printSteps;
    }

    @Override
    public void run()
    {
        while (!bagOfPresents.isEmpty())
        {
            Present pulledPresent;
            synchronized (bagOfPresents)
            {
                if (!bagOfPresents.isEmpty()) 
                { 
                    pulledPresent = bagOfPresents.remove(0); 
                }
                else 
                { 
                    pulledPresent = null; 
                }
            }

            if (pulledPresent != null)
            {
                list.add(pulledPresent);
                Integer tag = null;

                // if minotaur asks to check for a specific present in list, check for it and remove it if exists.
                // otherwise, just remove first from the list
                if (checkForPresent.compareAndSet(true, false))
                {
                    tag = (random.nextInt(numOfStartingPresents));
                    boolean exists = list.contains(tag);

                    if (exists) { removePresent(tag); }
                    else { removePresent(null); }
                }
                else
                {
                    removePresent(null);
                }
            }
        }

        // the unordered bag is empty, time to make sure if any is left and finish up
        // this should never
        while (!list.isEmpty())
        {
            removePresent(null);
        }
    }

    private void removePresent(Integer tag)
    {
        Present removedPresent;
        if (tag != null)
        {
            removedPresent = list.remove(tag);
            if (removedPresent != null && printSteps)
            {
                System.out.println("The present with tag [" + tag + "] exists.");
                System.out.println("Servant wrote Thank You for present [" + tag + "] and removed it.");
            }
        }
        else
        {
            removedPresent = list.removeFirstPresent();
            if (removedPresent != null && printSteps) 
            {
                System.out.println("Servant wrote Thank You for present [" + removedPresent.tag + "] and removed it.");
            }
        }
    }
}

public class MinotaurThanksYou {
    private static boolean printServantSteps = false;

    public static void main(String[] args) 
    {
       int numPresents = 500000;
       int numServants = 4;
        
        // Check for printing to be enabled
        for (String arg : args)
        {
            if (arg.equals("-p")) { printServantSteps = true; break; }
        }

        // allows for a command line arg from the user to determine number of guests, otherwise defaults to 10 guests, and if they want to print steps.
        try {
            numPresents = (args.length >= 1 && !args[0].equals("-p")) ? Integer.parseInt(args[0]) : numPresents;
            numServants = (args.length >= 2 && !args[1].equals("-p")) ? Integer.parseInt(args[1]) : numServants;
            
        } catch (Exception e)
        {
            System.err.println("One or more were incorrect values.");
            System.out.println("Using number of presents to be: " + numPresents);
            System.out.println("Using number of servants to be: " + numServants);
        }

        System.out.println("The Minotaur is now starting to thank his guests!!");
        long startTime = System.currentTimeMillis();

        ConcurrentLinkedList list = new ConcurrentLinkedList();
        ArrayList<Present> bigBagOfPresents = new ArrayList<>(numPresents);
        for (int i = 0; i < numPresents; i++)
        {
            bigBagOfPresents.add(new Present(i));
        }
        Collections.shuffle(bigBagOfPresents);

        Servant[] servants = new Servant[numServants];
        for (int i = 0; i < numServants; i++)
        {
            servants[i] = new Servant(list, bigBagOfPresents, printServantSteps);
            servants[i].start();
        }

        // Minotaur wants a random servant to check the big bag!
        Random random = new Random();
        while (!bigBagOfPresents.isEmpty())
        {
            int servant = random.nextInt(numServants);
            servants[servant].checkForPresent.set(true);
        }

        for (int i = 0; i < numServants; i++) {
            try {
                servants[i].join();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

        long endTime = System.currentTimeMillis();
        System.out.println("The minotaur has thanked everyone.. now they're done being nice.");
        System.out.println("This took " + (endTime - startTime) + "ms.");
    }
}

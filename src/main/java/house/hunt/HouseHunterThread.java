package house.hunt;

import javax.mail.MessagingException;

import com.sun.mail.imap.IMAPFolder;

public class HouseHunterThread extends Thread {
    private final HouseHunterService houseHuntService;

    public HouseHunterThread(HouseHunterService houseHuntService) {
        super();
        this.houseHuntService = houseHuntService;
        this.houseHuntService.addEmailListener();
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {

            try {
            	houseHuntService.ensureOpen();
            	houseHuntService.clearEmailState();
                ((IMAPFolder) houseHuntService.getInbox()).idle();
            } catch (MessagingException e) {
            	e.printStackTrace();
            	try {
					Thread.sleep(100);
				} catch (InterruptedException e1) {}
            }

        }
    }
}

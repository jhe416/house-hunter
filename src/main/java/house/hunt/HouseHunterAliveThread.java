package house.hunt;

import java.util.Date;

import javax.mail.MessagingException;

public class HouseHunterAliveThread extends Thread{

    private static final long KEEP_ALIVE_FREQ = 15*60*1000; // 15 minutes

    private final HouseHunterService houseHuntService;

    public HouseHunterAliveThread(HouseHunterService houseHuntService) {
    	super();
        this.houseHuntService = houseHuntService;
    }
    
	@Override
	public void run() {
		while(!Thread.currentThread().isInterrupted()) {
			try {
				Thread.sleep(KEEP_ALIVE_FREQ);
				System.out.println(String.format("%s checking connection status...", HouseHunterService.DATE_FORMAT.format(new Date())));
				houseHuntService.reestablishConnection();
				System.out.println(String.format("%s connection is alive!", HouseHunterService.DATE_FORMAT.format(new Date())));
			} catch (InterruptedException | MessagingException e) {
				e.printStackTrace();
				try {
					Thread.sleep(1000);
				} catch (InterruptedException e1) {}
			}
		}
	}
}

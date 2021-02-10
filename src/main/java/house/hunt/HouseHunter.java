package house.hunt;

public class HouseHunter {
	public static void main(String[] args) {
		try {
			HouseHunterService service = new HouseHunterService();			
			HouseHunterThread mainThread = new HouseHunterThread(service);
			HouseHunterAliveThread aliveThread = new HouseHunterAliveThread(service);
			
			mainThread.start();
			aliveThread.start();

			//now let the main thread taking care of the send scheduled email if is setup in config
			service.sendEmailAtThisTime();
			
			//now main waits for the main thread;
			mainThread.join();
			//when main thread is done turn of the keep alive thread
			if(aliveThread.isAlive()) {
				aliveThread.interrupt();
			}
		}catch (Exception e) {
			e.printStackTrace();
		}
	}
}
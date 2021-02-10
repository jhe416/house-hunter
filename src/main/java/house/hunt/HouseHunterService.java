package house.hunt;

import java.io.IOException;
import java.text.DateFormat;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.HashSet;
import java.util.Locale;
import java.util.Properties;
import java.util.Set;

import javax.activation.DataHandler;
import javax.activation.DataSource;
import javax.activation.FileDataSource;
import javax.mail.Address;
import javax.mail.BodyPart;
import javax.mail.Folder;
import javax.mail.Message;
import javax.mail.MessagingException;
import javax.mail.Multipart;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.Store;
import javax.mail.Transport;
import javax.mail.event.MessageCountAdapter;
import javax.mail.event.MessageCountEvent;
import javax.mail.internet.AddressException;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeBodyPart;
import javax.mail.internet.MimeMessage;
import javax.mail.internet.MimeMultipart;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.validator.EmailValidator;
import org.jsoup.Jsoup;

import com.sun.mail.imap.IMAPFolder;
import com.sun.mail.imap.IMAPStore;

@SuppressWarnings("deprecation")
public class HouseHunterService {
	private static final EmailValidator emailValidator = EmailValidator.getInstance();
	private static final String EMAIL_CONFIG_PATH = "/Users/jhe/GitHub/house-hunter/src/main/resources/email_config.properties";
	private static final String CONFIG_PATH = "/Users/jhe/GitHub/house-hunter/src/main/resources/config.properties";

	private final IMAPStore store;
	private final Folder inbox;
	private final Session session;
	private final String email;
	private final String password;
	private final String defaultUnitType;
	private final int confirmationWaitTime;
	private final boolean confirmationWaitTimeEnable;
	
	//direct email send configs:
	private final boolean scheduleEmailSend;
	private final String scheduleTime;
	private final String scheduleSendTo;
	private final String scheduleSendLocation;
	private final String scheduleSendUnitType;
	private final boolean scheduleSendWithPhotoId;
	private final boolean sendToMockEmail;
	
	
	
	private final String mockEmail;
	
	
	private static final String IMAPS = "imaps", INBOX = "INBOX", MULTIPART = "multipart/*", AT="@" ,NO_REPLY="no-reply", 
								TEXT_PLAIN="text/plain", TEXT_HTML = "text/html", MA_EMAIL = "sophlinmo@hotmail.com", SEATON="seaton", MEADOWS="meadows",REPLY_HEADER="References", In_Reply_To_Header="In-Reply-To";;
	
	private final Set<String> skipEmails;
	private Set<String> photoIdIndication;
	
	private static final Set<Character> SKIP_KEYS = new HashSet<>(Arrays.asList('<','>',':','*'));
	
	//email state
	private boolean emailSent;
	private Date timeSent;
	
	private static final Set<String> CONTACT_INDICATIONS = new HashSet<>(Arrays.asList("appointment","available","release","contact","book","submit","call","phone","registration"));
	private boolean contactIndicated;
	
	private static final Set<String> SEND_INDICATIONS = new HashSet<>(Arrays.asList("open","now","today","released"));
	private boolean sendIndicated;
	
	private static final Set<String> ONE_OF_US = new HashSet<>(Arrays.asList("jay.he.nokia@gmail.com","jay.siyuan.he@gmail.com","jay.siyuan.he@outlook.com","sophlinmo@hotmail.com"));
	private final Set<String> builders;
	private Set<String> siteLocations;
	private boolean builderIndicated;
	private boolean foundGodzilla;
	private final String godzilla;
	
	//properties 
	private final Properties prop;
	public static final DateFormat DATE_FORMAT = new SimpleDateFormat("dd-M-yyyy hh:mm:ss",Locale.getDefault());
	
	public HouseHunterService() throws MessagingException, IOException {
		
		this.prop = LoadConfigFile.getConfigProperties(CONFIG_PATH);
		this.email = prop.getProperty("userEmail");
		System.out.println(String.format("Who let the dogs out?.....(%s)", email));
		
		this.password = prop.getProperty("password");
		this.defaultUnitType = prop.getProperty("unitType");
		
		this.scheduleEmailSend = Boolean.valueOf(prop.getProperty("scheduleEmailSend"));
		this.scheduleTime = prop.getProperty("scheduleTime");
		this.scheduleSendTo = prop.getProperty("scheduleSendTo");
		this.scheduleSendWithPhotoId = Boolean.valueOf(prop.getProperty("scheduleSendWithPhotoId"));
		this.confirmationWaitTime = Integer.valueOf(prop.getProperty("confirmationWaitTime"));
		this.sendToMockEmail = Boolean.valueOf(prop.getProperty("sendToMockEmail"));
		this.mockEmail = prop.getProperty("mockEmail");
		
		String builderStr = prop.getProperty("builders");
		this.builders = new HashSet<>(Arrays.asList(builderStr.split(",")));
		
		this.scheduleSendLocation = prop.getProperty("scheduleSendLocation");
		this.scheduleSendUnitType = prop.getProperty("scheduleSendUnitType");
		this.godzilla = prop.getProperty("godzilla");
		
		String skipEmailsStr = prop.getProperty("skipEmails");
		String[] emails = skipEmailsStr.split(",");
		this.skipEmails = new HashSet<>(Arrays.asList(emails));
		this.confirmationWaitTimeEnable = Boolean.valueOf(prop.getProperty("confirmationWaitTimeEnable"));
		
		this.photoIdIndication = new HashSet<>(Arrays.asList("copy","photo","id"));
		
		this.session = Session.getInstance(LoadConfigFile.getConfigProperties(EMAIL_CONFIG_PATH),
				new javax.mail.Authenticator() {
			protected PasswordAuthentication getPasswordAuthentication() {
				return new PasswordAuthentication(email, password);
			}
		});
		
		this.store = (IMAPStore) session.getStore(IMAPS);
		this.store.connect(email,password);
		this.inbox = (IMAPFolder) store.getFolder(INBOX);
		
		this.emailSent = false;
		this.contactIndicated=false;
		this.sendIndicated=false;
		this.builderIndicated=false;
		this.timeSent = null;
	}

	public void addEmailListener() {
		inbox.addMessageCountListener(new MessageCountAdapter() {
			@Override
			public void messagesAdded(MessageCountEvent event) {
				clearEmailState();
				try {
					processEvent(event.getMessages());
				} catch (MessagingException | IOException e) {
					e.printStackTrace();
				}
				
			}
		});
	}
	
	public void processEvent(Message[] messages) throws AddressException, MessagingException, IOException {
		if(!validateEmail(messages)) {
			System.out.println(String.format("%s reply email detected, skip sending appointment request. confirmation received??", DATE_FORMAT.format(new Date())));
			return;
		}
		
		Set<String> emailsToSend = initializeIndicationState();
		
		for (Message message : messages) {
			if(message.isMimeType(MULTIPART)) {
				if(parseMultiPartEmail((MimeMultipart) message.getContent(), emailsToSend, message)) return;
			}else if(message.isMimeType(TEXT_PLAIN)) {
				if(parseEmailandDecideToSendEmail(message.getContent().toString(), emailsToSend, message)) return;
			}else if(message.isMimeType(TEXT_HTML)) {
	            String html = (String) message.getContent();
				if(parseEmailandDecideToSendEmail(Jsoup.parse(html).text(), emailsToSend, message)) return;
			}
		}	
		
		//there will be a flag to turn this off
		if(this.isBuilderIndicated() && this.isContactIndicated() && this.isSendIndicated()) {
			for(Address addr : messages[0].getFrom()) {
				String senderEmail = ((InternetAddress) addr).getAddress();
				if(!skipEmails.contains(senderEmail) && !senderEmail.contains(NO_REPLY)) {
					emailsToSend.add(senderEmail);
				}
			}
			
			sendEmail(emailsToSend, this.photoIdIndication.isEmpty(),getLocationString(),getUnitTypeString());
		}
	}
	
	private boolean parseMultiPartEmail(MimeMultipart mimeMultipart, Set<String> emailsToSend, Message message) throws AddressException, MessagingException, IOException {
	    int count = mimeMultipart.getCount();
		for (int i = 0; i < count; i++) {
	        BodyPart bodyPart = mimeMultipart.getBodyPart(i);
	        String val = StringUtils.EMPTY;
	        if (bodyPart.isMimeType(TEXT_PLAIN)) {
	        	val = bodyPart.getContent().toString();
	        } else if (bodyPart.isMimeType(TEXT_HTML)) {
	            String html = (String) bodyPart.getContent();
	            val = Jsoup.parse(html).text();
	        } else if (bodyPart.getContent() instanceof MimeMultipart){
	            if(parseMultiPartEmail((MimeMultipart)bodyPart.getContent(), emailsToSend, message)) return true;
	            continue;
	        }
	        
        	if(parseEmailandDecideToSendEmail(val, emailsToSend, message)) return true;
	    }
	    
	    return false;
	}
	
	private boolean parseEmailandDecideToSendEmail(String val, Set<String> emailsToSend, Message message) throws AddressException, MessagingException {
		//regex sucks, just do it
		char[] char_arr = val.trim().toCharArray();
		for(int i=0;i<char_arr.length;i++) {
			if(SKIP_KEYS.contains(char_arr[i])) {
				char_arr[i] = ' ';
			}
		}

		val = new String(char_arr).replaceAll("\r\n", " ");
		String[] arr = val.split(" ");
		
		for(String str : arr) {
			if(str == null || str.isEmpty() || str.length() > 50) continue;
			str = str.trim().toLowerCase();
			if(str.isEmpty()) continue;
			if(str.charAt(str.length()-1) == '.') str = str.substring(0,str.length()-1);
			if(str.length() > 5 && str.contains(AT) && emailValidator.isValid(str) && !skipEmails.contains(str) && !str.contains(NO_REPLY)) {
				emailsToSend.add(str);
			}else if(!isContactIndicated() && CONTACT_INDICATIONS.contains(str)) {
				this.setContactIndicated(true);
			}else if(!isSendIndicated() && SEND_INDICATIONS.contains(str)) {
				this.setSendIndicated(true);
			}else if(!this.isBuilderIndicated() && builders.contains(str)) {
				this.setBuilderIndicated(true);
			}else if(photoIdIndication.contains(str)) {
				photoIdIndication.remove(str);
			}else if(str.contains(SEATON)) {
				this.siteLocations.add(SEATON);
			}else if(str.contains(MEADOWS)) {
				this.siteLocations.add(MEADOWS);
			}
			
			if(!isFoundGodzilla() && godzilla.equals(str)) {
				this.setFoundGodzilla(true);
			}
		}

		if(this.confirmationWaitTimeEnable && this.isBuilderIndicated() && this.isEmailSent()) {
			Date now = new Date();
			long timeDiff = now.getTime() - timeSent.getTime();
			if(timeDiff/(1000 * 60) <= confirmationWaitTime) {
				System.out.println(String.format("%s email sent within the last %d minutes, please wait", DATE_FORMAT.format(new Date()), confirmationWaitTime));
				return true;
			}				
		}
		
		return false;
	}
	
	private boolean validateEmail(Message[] messages) throws MessagingException {
		for (Message message : messages) {
			if(message.getHeader(REPLY_HEADER) != null || message.getHeader(In_Reply_To_Header) != null) {
				for(Address addr : message.getFrom()) {
					String senderEmail = ((InternetAddress) addr).getAddress();
					if(ONE_OF_US.contains(senderEmail)) return true;
				}
				return false;
			}
		}
		return true;
	}

	private String getLocationString() {
		if(isFoundGodzilla()) {
			return prop.getProperty("godzillaLocation");
		}
		
		StringBuilder sb = new StringBuilder();
		int i=0;
		for(String site : siteLocations) {
			if(site.equals(SEATON)) {
				sb.append(prop.getProperty("seatonLocation"));
			}else if(site.equals(MEADOWS)) {
				sb.append(prop.getProperty("meadowsLocation"));
			}
			if((i++)+1 < siteLocations.size()) {
				sb.append(" and ");
			}
		}
		
		if(sb.length() == 0) {
			return String.format("%s and %s", prop.getProperty("seatonLocation"), prop.getProperty("meadowsLocation"));
		}
		
		return sb.toString();
	}
	
	private String getUnitTypeString() {
		if(isFoundGodzilla()) {
			return prop.getProperty("godzillaUnitType");
		}
		
		return defaultUnitType;
	}
	
	public boolean sendEmail(Set<String> emailsToSend, boolean attachPhotoId, String location, String unitType) throws AddressException, MessagingException {
		MimeMessage newMessaage = new MimeMessage(session);

		// Set From: header field of the header.
		newMessaage.setFrom(new InternetAddress(email));

		// Set To: header field of the header.
		Address[] recipients = new Address[emailsToSend.size()];
		int i=0;
		for(String email : emailsToSend) {
			recipients[i++] = new InternetAddress(email);
		}
		newMessaage.addRecipients(Message.RecipientType.TO, sendToMockEmail? new Address[] {new InternetAddress(mockEmail)} : recipients);
		
		// Set Subject: header field
		newMessaage.setSubject(String.format("House wanted! Seek appointment at the %s for %s units", location, unitType));

		// Now set the actual message
		StringBuilder sb = new StringBuilder();
		sb.append("Hi there,");
		sb.append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append(String.format("We are Jessica and Jay and we are extremely interested in purchasing the %s units at the %s. ", unitType, location));
		sb.append(System.lineSeparator());
		sb.append("We would like to book an appointment to move forward with the purchase deposit.");
		sb.append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("Please find our contact information below:");
		sb.append(System.lineSeparator());
		sb.append("Phone number: 6136008133, 6475321161, (please call 6136008133 first, I am available at any time).");
		sb.append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("Thank you, looking forward to hearing from you");
		sb.append(System.lineSeparator());
		sb.append(System.lineSeparator());
		sb.append("Jessica(Shuli Lin) and Jay");
		
		if(attachPhotoId) {
			BodyPart messageBodyPart = new MimeBodyPart();
			messageBodyPart.setText(sb.toString());

			// Create a multipar message
			Multipart multipart = new MimeMultipart();

			// Set text message part
			multipart.addBodyPart(messageBodyPart);

			// Part two is attachment
			messageBodyPart = new MimeBodyPart();

			DataSource source = new FileDataSource(prop.getProperty("idPath"));
			messageBodyPart.setDataHandler(new DataHandler(source));
			messageBodyPart.setFileName("my_id.jpeg");
			multipart.addBodyPart(messageBodyPart);

			// Send the complete message parts
			newMessaage.setContent(multipart);
		}else {
			newMessaage.setText(sb.toString());
		}

		// Send message
		Transport.send(newMessaage);
		System.out.println(String.format("%s email%s with subject %s sent successful, waitting for confirmation", DATE_FORMAT.format(new Date()), this.isFoundGodzilla()? String.format("(%s)",godzilla) : StringUtils.EMPTY, newMessaage.getSubject()));
		this.setEmailSent(true);
		this.setTimeSent(new Date());
		
		return true;
	}
	
	public void sendEmailAtThisTime() throws ParseException, AddressException, MessagingException, InterruptedException {
		if(!scheduleEmailSend) return;
		if(StringUtils.isEmpty(scheduleTime) || StringUtils.isEmpty(scheduleSendTo) || StringUtils.isEmpty(scheduleSendLocation) || StringUtils.isEmpty(scheduleSendUnitType)) return;
		
		Date dateToSend = DATE_FORMAT.parse(scheduleTime);

		System.out.println(String.format("%s schedule to send email to %s at %s", DATE_FORMAT.format(new Date()), scheduleSendTo, scheduleTime));
		while(new Date().before(dateToSend)) {
			Thread.sleep(100);
		}
		
		//make sure to configure the location and unit type;
		String[] emails = scheduleSendTo.split(",");
		Set<String> submitEmails = new HashSet<>();
		for(String email : emails) {
			if(emailValidator.isValid(email)) {
				submitEmails.add(email);
			}
		}

		if(submitEmails.isEmpty()) {
			System.out.println("Provided builder emails are not valid");
			return;
		}
		
		submitEmails.add(MA_EMAIL);
		sendEmail(submitEmails, scheduleSendWithPhotoId, scheduleSendLocation, scheduleSendUnitType);
		System.out.println(String.format("%s schedule to send email completed", DATE_FORMAT.format(new Date())));
	}
	
    public void closeInbox() {
        try {
            if (inbox != null && inbox.isOpen()) {
                inbox.close(false);
            }
        } catch (final Exception e) {
            // ignore
        }
    }

    public void closeStore() {
        try {
            if (store != null && store.isConnected()) {
                store.close();
            }
        } catch (final Exception e) {
        	// ignore
        }
    }

    public void ensureOpen() throws MessagingException {

        if (inbox != null) {
            Store store = inbox.getStore();
            if (store != null && !store.isConnected()) {
                store.connect(email, password);
            }
        } else {
            throw new MessagingException("Unable to open a null folder");
        }

        if (inbox.exists() && !inbox.isOpen() && (inbox.getType() & Folder.HOLDS_MESSAGES) != 0) {
            inbox.open(Folder.READ_ONLY);
            if (!inbox.isOpen())
                throw new MessagingException("Unable to open folder " + inbox.getFullName());
        }
    }
    
    public void clearEmailState() {
    	if(this.isEmailSent()) {
			long timeDiff = new Date().getTime() - timeSent.getTime();
			if(timeDiff/(1000 * 60) > confirmationWaitTime) {
				this.setEmailSent(false);
				this.setTimeSent(null);
			}	
    	}
    }

    public Set<String> initializeIndicationState() {
    	this.setContactIndicated(false);
    	this.setBuilderIndicated(false);
    	this.setSendIndicated(false);
    	this.setFoundGodzilla(false);
    	this.photoIdIndication = new HashSet<>(Arrays.asList("copy","photo","id"));
    	this.siteLocations = new HashSet<>();
    	return new HashSet<>(Arrays.asList(MA_EMAIL));
    }
    
    public int reestablishConnection() throws MessagingException {
    	return this.getInbox().getMessageCount();
    }
    
	public IMAPStore getStore() {
		return store;
	}

	public Folder getInbox() {
		return inbox;
	}

	public Session getSession() {
		return session;
	}

	public String getEmail() {
		return email;
	}
	
	public String getPassword() {
		return password;
	}

	public boolean isEmailSent() {
		return emailSent;
	}

	public void setEmailSent(boolean emailSent) {
		this.emailSent = emailSent;
	}

	public Date getTimeSent() {
		return timeSent;
	}

	public void setTimeSent(Date timeSent) {
		this.timeSent = timeSent;
	}

	public boolean isContactIndicated() {
		return contactIndicated;
	}

	public void setContactIndicated(boolean contactIndicated) {
		this.contactIndicated = contactIndicated;
	}

	public boolean isSendIndicated() {
		return sendIndicated;
	}

	public void setSendIndicated(boolean sendIndicated) {
		this.sendIndicated = sendIndicated;
	}

	public boolean isBuilderIndicated() {
		return builderIndicated;
	}

	public void setBuilderIndicated(boolean builderIndicated) {
		this.builderIndicated = builderIndicated;
	}

	public Set<String> getPhotoIdIndication() {
		return photoIdIndication;
	}

	public void setPhotoIdIndication(Set<String> photoIdIndication) {
		this.photoIdIndication = photoIdIndication;
	}

	public Set<String> getSiteLocations() {
		return siteLocations;
	}

	public void setSiteLocations(Set<String> siteLocations) {
		this.siteLocations = siteLocations;
	}

	public boolean isFoundGodzilla() {
		return foundGodzilla;
	}

	public void setFoundGodzilla(boolean foundGodzilla) {
		this.foundGodzilla = foundGodzilla;
	}
}

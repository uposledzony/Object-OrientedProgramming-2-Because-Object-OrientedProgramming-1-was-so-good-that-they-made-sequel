package LimakWebApp.ServerSide;

import LimakWebApp.DataPackets.CredentialPacket;
import LimakWebApp.Utils.Constants;
import LimakWebApp.Utils.Controller;
import LimakWebApp.Utils.MessageTemplates;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.Session;
import javax.mail.Transport;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import java.util.Date;
import java.util.Properties;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <h1>EmailUtil</h1>
 * This class is used by:
 * {@link Server}
 * and
 * {@link MainPageController}
 * to send email notifications tp users
 *
 * @author  Kamil Chrustowski
 * @version 1.0
 * @since   13.08.2019
 */
public class EmailUtil {

    private Session session;
    private ExecutorService pool;
    private  boolean sessionExists = false;
    private Controller controller;

    /**
     * Constructor of EmailUtil
     * @param controller Controller to get data, send and set logs
     */
    public EmailUtil(Controller controller){
        this.controller = controller;
    }

    /**
     * Utility method to send simple HTML email
     * @param purpose indicates email's body content and header (greeting new user or new files notification
     * @param toEmail indicates the receiver of message
     * @param files array of file names to send in notification
     * <p> Method uses
     * {@link ExecutorService}
     * to submit task of sending email</p>
     */
    public void sendEmail(CredentialPacket toEmail, Boolean purpose, String...files) {
        Runnable task = () -> {
            sendMessage(toEmail, purpose, files);
        };
        pool.submit(task);
    }

    private void sendMessage(CredentialPacket toEmail, Boolean purpose, String...files){
        try {
            MessageTemplates messageTemplates = new MessageTemplates(toEmail, purpose, files);
            MimeMessage msg = new MimeMessage(session);
            msg.addHeader("Content-type", "text/HTML; charset=UTF-8");
            msg.addHeader("format", "flowed");
            msg.addHeader("Content-Transfer-Encoding", "8bit");
            msg.setFrom(new InternetAddress(Constants.getServerEMail(this), "NoReply"));
            msg.setReplyTo(InternetAddress.parse(toEmail.getUserEmail(), false));
            msg.setSubject(messageTemplates.getSubject(this), "UTF-8");
            msg.setText(messageTemplates.getContents(this), "UTF-8");
            msg.setSentDate(new Date());
            msg.setRecipients(Message.RecipientType.TO, InternetAddress.parse(toEmail.getUserEmail(), false));
            Transport.send(msg);
            controller.setStatusText("Email sent to: " + toEmail.getUserName());
            controller.addLog(Constants.LogType.INFO, new Date().toString() + ":\nEmail sent to: \n\t"+ toEmail.getUserName());
        } catch (Exception e) {
            controller.setStatusText("Email hasn't been sent successfully!!");
            StringBuilder stringBuilder = new StringBuilder();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream outStream = new PrintStream(outputStream);
            e.printStackTrace(outStream);
            stringBuilder.append(new Date()).append(":\n").append("Email hasn't been sent successfully!! \n\t").append(e.getMessage()).append("\n").append(outStream.toString()).append("\n");
            controller.addLog(Constants.LogType.ERROR, stringBuilder.toString());
            e.printStackTrace();
        }
    }

    /**
     * Initializes session for testing purpose
     */
    public void testSession(){
        pool = Executors.newFixedThreadPool(2);
        initSessionForTests();
    }

    private void initSessionForTests(){
        final String fromEmail = Constants.getServerEMail(this);
        final String sessionPassword = "XsW2#eDc!qAz";
        Properties props = new Properties();
        props.put("mail.smtp.host", "smtp.gmail.com");
        props.put("mail.smtp.port", "587");
        props.put("mail.smtp.auth", "true");
        props.put("mail.smtp.starttls.enable", "true");
        Authenticator auth = new Authenticator() {
            protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                return new javax.mail.PasswordAuthentication(fromEmail, sessionPassword);
            }
        };
        session = Session.getInstance(props, auth);
        sessionExists = true;
    }

    void createSession(String password){
        if(!sessionExists) {
            pool = Executors.newFixedThreadPool(2);
            final String fromEmail = Constants.getServerEMail(this);
            final String sessionPassword = password;
            Properties props = new Properties();
            props.put("mail.smtp.host", "smtp.gmail.com");
            props.put("mail.smtp.port", "587");
            props.put("mail.smtp.auth", "true");
            props.put("mail.smtp.starttls.enable", "true");
            Authenticator auth = new Authenticator() {
                protected javax.mail.PasswordAuthentication getPasswordAuthentication() {
                    return new javax.mail.PasswordAuthentication(fromEmail, sessionPassword);
                }
            };
            session = Session.getInstance(props, auth);
            sessionExists = true;
        }
    }


    /**
     * Drops email session
     */
    public void dropSession(){
        sessionExists = false;
        pool.shutdown();
        try {
            if(!pool.awaitTermination(10, TimeUnit.SECONDS)){
                pool.shutdownNow();
            }
        }
        catch(InterruptedException ie){
            pool.shutdownNow();
            Thread.currentThread().interrupt();
        }
        session = null;
    }
}
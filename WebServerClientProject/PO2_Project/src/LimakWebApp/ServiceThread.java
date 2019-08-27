package LimakWebApp;

import LimakWebApp.Utils.Constants;

import javafx.application.Platform;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;

import java.net.Socket;

import java.util.Date;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * <h1>ServiceThread</h1>
 * This class performs sockets transfer operations
 * @author  Kamil Chrustowski
 * @version 1.0
 * @since   02.07.2019
 */
public class ServiceThread {

    private ServicesHandler parentHandler;
    private ExecutorService transferService = Executors.newFixedThreadPool(1);
    private Socket socket;
    private String type;
    private String token;
    private volatile ObjectInputStream inputStream;
    private volatile ObjectOutputStream outputStream;
    private volatile Boolean sendExit = false;

    /**
     * Constructor of ServiceThread, sets socket, gets I/O streams.
     * @param socket socket to set
     * @param type type of services to perform
     * @param parent the creator of new instance
     * @throws IOException if there are problems with connections
     */
    public ServiceThread(Socket socket, String type, ServicesHandler parent)throws IOException {
        parentHandler = parent;
        this.socket = socket;
        this.type = type;
        outputStream = new ObjectOutputStream(this.socket.getOutputStream());
        outputStream.flush();
        inputStream = new ObjectInputStream(this.socket.getInputStream());
        token = this.type.equals("FileService") ? "file" : (this.type.equals("NotificationService") ? "notification" : "authorization packet");
    }

    /**
     * This method gets object or objects
     * @param conditionalIgnored Indicates if method should get object continuously or perform action one time.
     */
    public synchronized void getObject(boolean conditionalIgnored) {
        Runnable task = () -> {
            do {
                if(sendExit || socket.isClosed() || socket.isInputShutdown()) break;
                synchronized (inputStream) {
                    try {
                        Object object = inputStream.readObject();
                        parentHandler.processObject(object);
                    } catch (ClassNotFoundException cNFE) {
                        Platform.runLater(() -> {
                            parentHandler.getController().setStatusText("Can't get the " + token + "!");
                            StringBuilder stringBuilder = new StringBuilder();
                            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                            PrintStream outStream = new PrintStream(outputStream);
                            cNFE.printStackTrace(outStream);
                            stringBuilder.append(new Date())
                                    .append(":\n").append("Can't get the ")
                                    .append(token).append("\n\t")
                                    .append(cNFE.getMessage()).append("\n")
                                    .append(outStream.toString()).append("\n");
                            parentHandler.getController().addLog(Constants.LogType.ERROR, stringBuilder.toString());
                        });
                    } catch (IOException ignored) {
                    }
                }
            }
            while(conditionalIgnored);
        };
        if(!conditionalIgnored) {
            parentHandler.getController().getPool().submit(task);
        }
        else {
            parentHandler.submitNotificationWatcher(task);
        }
    }

    /**
     * This method sends object via socket.
     * @param object Object to send
     */
    public synchronized void sendObject(Object object) {
        if(socket.isClosed() || socket.isOutputShutdown()) return;
        Runnable task = () -> {
            try  {
                synchronized (outputStream) {
                    outputStream.writeObject(object);
                    outputStream.flush();
                }
            } catch (IOException io) {
                io.printStackTrace();
                Platform.runLater(() -> {
                    parentHandler.getController().setStatusText("Can't send the " + token + "!");
                    StringBuilder stringBuilder = new StringBuilder();
                    ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                    PrintStream outStream = new PrintStream(outputStream);
                    io.printStackTrace(outStream);
                    stringBuilder.append(new Date())
                            .append(":\n").append("Can't send the ")
                            .append(token).append("\n\t")
                            .append(io.getMessage()).append("\n")
                            .append(outStream.toString()).append("\n");
                    parentHandler.getController().addLog(Constants.LogType.ERROR, stringBuilder.toString());
                });
            }
        };
        transferService.submit(task);
    }

    /**
     * This method submit task to owned thread pool.
     * @param task task to perform
     */
    public synchronized void submitTask(Runnable task){
        synchronized (transferService) {
            transferService.submit(task);
        }
    }

    /**
     * This method indicates if loop in getObject should stop operations
     * @param value value to set
     */
    public synchronized  void setSendExit(Boolean value){
        sendExit = value;
    }

    void cleanUp(){
        try {
            inputStream.close();
            outputStream.close();
            transferService.shutdown();
            if(!transferService.isTerminated())
                this.transferService.awaitTermination(10, TimeUnit.SECONDS);
        }
        catch(InterruptedException|IOException e){
            Platform.runLater(() -> {
                transferService.shutdownNow();
                parentHandler.getController().setStatusText("Can't terminate service!");
                StringBuilder stringBuilder = new StringBuilder();
                ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                PrintStream outStream = new PrintStream(outputStream);
                e.printStackTrace(outStream);
                stringBuilder.append(new Date())
                        .append(":\n").append("Can't terminate service!")
                        .append("\n\t")
                        .append(e.getMessage()).append("\n")
                        .append(outStream.toString()).append("\n");
                parentHandler.getController().addLog(Constants.LogType.ERROR, stringBuilder.toString());
            });
        }
    }
}

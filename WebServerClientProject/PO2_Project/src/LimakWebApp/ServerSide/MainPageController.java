package LimakWebApp.ServerSide;

import LimakWebApp.DataPackets.CredentialPacket;

import LimakWebApp.Utils.AbstractServerController;
import LimakWebApp.Utils.Constants;
import LimakWebApp.Utils.DataPair;
import LimakWebApp.Utils.StringFunctionalInterface;

import com.google.gson.Gson;
import com.google.gson.JsonParseException;
import com.google.gson.internal.LinkedTreeMap;
import com.google.gson.stream.JsonReader;

import javafx.scene.control.ListView;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.control.TreeCell;
import javafx.scene.control.TreeItem;
import javafx.scene.control.TreeView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.text.Text;

import javafx.application.Platform;

import javafx.beans.property.ListProperty;
import javafx.beans.property.SimpleListProperty;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;

import javafx.fxml.FXML;

import java.awt.Desktop;

import java.io.BufferedWriter;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintStream;
import java.io.Writer;

import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.stream.Collectors;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * <h1>MainPageController</h1>
 * This class performs GUI operations and some business logic for Server
 * @author  Kamil Chrustowski
 * @version 1.0
 * @since   20.06.2019
 */
public class MainPageController extends AbstractServerController {

    @FXML
    private TreeView<File> serverTreeView;
    @FXML
    private volatile TextField  serverStatusText;
    @FXML
    private volatile ScrollPane log;
    @FXML
    private volatile VBox logContent;
    @FXML
    private volatile ListView<DataPair> listView;

    private final Object lock = new Object();
    private static Server server;
    private volatile CredentialPacket credentialPacket;
    private ScheduledExecutorService scheduler;
    private EmailUtil emailSession;
    private volatile ListProperty<DataPair> listProperty;
    private volatile ConcurrentHashMap<CredentialPacket, ArrayList<String>> runTimeMapOfFileOwners;
    private volatile ListOfClients listOfClients;
    private volatile ArrayList<DiskMap> serverDiskMap;
    private volatile ObservableList<DataPair> packetBooleanObservableList;
    private volatile Set<String> ids;

    /**
     * Constructor of {@link MainPageController}, initializes {@link Server}, set of string and calls super()
     */
    public MainPageController(){
        super();
        ids = new HashSet<>();
        server = new Server();
        server.setController(this);
    }

    @FXML
    private void initialize(){
        this.credentialPacket = (new CredentialPacket(Constants.getServerEMail(this), "Server", Constants.getServerDirectory(this)));
        String[] folderNames = Constants.getDirectories(this);
        createServerDirectoriesIfNotExist(folderNames);
        createImportantStartUpFilesIfNotExist();
        displayTreeView();
        listProperty = new SimpleListProperty<>();
        listOfClients = new ListOfClients();
        runTimeMapOfFileOwners = new ConcurrentHashMap<>();
        readInitListOfClientsFromFile();
        serverDiskMap = new ArrayList<>();
        emailSession = new EmailUtil(this);
        emailSession.createSession("XsW2#eDc!qAz");
        for(int i = 0; i < 5; ++i){
            serverDiskMap.add(new DiskMap(credentialPacket.getUserFolderPath() + "\\" + Constants.getDirectories(this)[i]));
            serverDiskMap.get(i).setDataMap(readContentsFromJson(i));
        }
        listOfClients.put(credentialPacket, true);
        packetBooleanObservableList = FXCollections.observableArrayList(listOfClients.toDataPairSet());
        listProperty.set(packetBooleanObservableList);
        listView.itemsProperty().bindBidirectional(listProperty);
        log.setContent(logContent);
        fillRunTimeMap();
        scheduler = Executors.newScheduledThreadPool(4);
        Runnable task = ()-> dumpListOfClients();
        Runnable task2 = ()->{
            for(int i = 0; i < serverDiskMap.size(); ++i){
                dumpContentsToJson(i);
            }
        };
        Runnable task3 = ()->checkIfAreFilesToSendForInactiveUsers();
        Runnable task1 = ()->{
          setStatusText("Waiting for the clients...");
        };
        scheduler.scheduleAtFixedRate(task, 30, 120, SECONDS);
        scheduler.scheduleAtFixedRate(task2, 10, 60, SECONDS);
        scheduler.scheduleAtFixedRate(task3,180 , 7200, SECONDS);
        scheduler.scheduleAtFixedRate(task1, 10, 60, SECONDS);
        setStatusText("Waiting for the clients...");
        addLog(Constants.LogType.INFO, new Date().toString() + ":\nWaiting for the clients...\n");
    }

    /**
     * This method adds given filename to RunTimeMap, provided that given user is valid.
     * @param user Owner of file represented by filename.
     * @param fileName File to add.
     */
    @Override
    public void addToRuntimeMap(CredentialPacket user, String fileName){
        if(runTimeMapOfFileOwners.isEmpty()){
            ArrayList<String> list = new ArrayList<>();
            list.add(fileName);
            runTimeMapOfFileOwners.put(user, list);
            return;
        }
        boolean found = false;
        for (Map.Entry<CredentialPacket, ArrayList<String>> next : runTimeMapOfFileOwners.entrySet()) {
            if (next.getKey().equals(user)) {
                next.getValue().add(fileName);
                found = true;
                break;
            }
        }
        if(!found) {
            ArrayList<String> list = new ArrayList<>();
            list.add(fileName);
            runTimeMapOfFileOwners.put(user, list);
        }
    }

    /**
     * This method removes user from list of owners of given file. Returns true if method succeeded to remove user.
     * @param fileName The file owned by provided user
     * @param user The user whose ownership to provided file will be abolished
     * @return boolean
     */
    @Override
    public synchronized boolean removeUserFromFileOwners(String fileName, CredentialPacket user){
        boolean rV = false;
        for(DiskMap disk : serverDiskMap){
            if(disk.checkIfFileExists(fileName)){
                rV = disk.removeFileOwner(fileName, user);
                break;
            }
        }
        return rV;
    }

    /**
     * This method adds user to owners of given file and if user is active the file is added to runtime map.
     * @param to The user who file would be shared with.
     * @param item File to share.
     */
    @Override
    public void shareFile(CredentialPacket to, String item){
        addUserToFileOwners(item,to);
        if(getActiveListOfClients().contains(to)){
            if(!runTimeMapOfFileOwners.get(to).contains(item)){
                runTimeMapOfFileOwners.get(to).add(item);
            }
            server.shareToUser(to, item);
        }
    }

    /**
     * This method return the list of logged out users
     * @return {@code ArrayList<CredentialPacket>}
     */
    @Override
    public ArrayList<CredentialPacket> getInactiveListOfClients(){
        return listOfClients.entrySet().stream()
                .filter(client-> !client.getValue() && !client.getKey().getUserName().equals("Server"))
                .map(client->client.getKey())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * This method returns the list of legged in users
     * @return {@code ArrayList<CredentialPacket>}
     */
    @Override
    public ArrayList<CredentialPacket> getActiveListOfClients(){
        return listOfClients.entrySet().stream()
                .filter(client->client.getValue() && !client.getKey().getUserName().equals("Server"))
                .map(client->client.getKey())
                .collect(Collectors.toCollection(ArrayList::new));
    }

    /**
     * This method returns set of filenames owned by given user.
     * @param packet the user who owns some files
     * @return {@code Set<String>}
     */
    @Override
    public synchronized Set<String> getListOfFilesForUser(CredentialPacket packet){
        Set<String> rV = new HashSet<>();
        for(DiskMap disk : serverDiskMap){
            if(disk == null || disk.getMap() == null || disk.getMap().isEmpty()) continue;
            rV.addAll(disk.getListOfFilesForGivenUser(packet));
        }
        return rV;
    }

    /**
     * This method returns list of files missing in files' list received from user.
     * @param user Owner of list to compare
     * @param userFileList list of filenames to compare
     * @return {@code Map.Entry<CredentialPacket, ArrayList<String>>}
     */
    @Override
    public Map.Entry<CredentialPacket, ArrayList<File>> compareUserAndServerList(CredentialPacket user, ArrayList<String> userFileList){
        Map.Entry<CredentialPacket, ArrayList<File>> rV = null;
        ArrayList<File> outList = new ArrayList<>();
        ArrayList<String> tmp = new ArrayList<>(getListOfFilesForUser(user));
        for(String fileName: tmp){
            if(!userFileList.contains(fileName)){
                outList.add(new File(findFileInServer(fileName) ,fileName));
            }
        }
        rV = new AbstractMap.SimpleEntry<>(user, outList);
        return rV;
    }

    /**
     * This method cleans connections, drops email session, dumps server control files, and shutdowns {@link ScheduledExecutorService}
     */
    @Override
    public void cleanUp(){
        dumpListOfClients();
        for(int i = 0; i < serverDiskMap.size(); ++i){
            dumpContentsToJson(i);
        }
        setStatusText("Closing...");
        addLog(Constants.LogType.INFO, new Date().toString() + ":\nClosing...\n");
        emailSession.dropSession();
        server.setItTimeToStop(true);
        scheduler.shutdown();
        pool.shutdown();
        try {
            scheduler.awaitTermination(10, SECONDS);
            pool.awaitTermination(10, SECONDS);
        }
        catch(InterruptedException ie){
            scheduler.shutdownNow();
            pool.shutdownNow();
        }
        server.quit();
    }

    /**
     * This method puts an ID to hash set of IDs.
     * @param strId unique ID to put
     */
    @Override
    public synchronized void putId(String strId){
        Platform.runLater(()->{ synchronized(lock){ids.add(strId);}});
    }

    /**
     * This method checks if file is on server and returns directory name succeeded to find
     * @param fileName File to search
     * @return String
     */
    @Override
    public String findFileInServer(String fileName){
        for(String dir : Constants.getDirectories(this)){
            String outName = credentialPacket.getUserFolderPath() + "\\" + dir;
            File tmp = new File(outName);
            if(tmp.exists() && tmp.isDirectory()){
                for(String file : tmp.list()){
                    if(file.equals(fileName)){
                        return outName;
                    }
                }
            }
        }
        return "";
    }

    /**
     * This method updates list of clients for given user and status
     * @param packet User to update its state
     * @param value State, if true the user is on-line otherwise user is off-line
     */
    @Override
    public synchronized void updateListOfClients(CredentialPacket packet, Boolean value){
        synchronized (lock) {
            listOfClients.put(packet, value);
        }
        Platform.runLater(()-> {
            synchronized (lock) {
                packetBooleanObservableList.clear();
                packetBooleanObservableList.addAll(listOfClients.toDataPairSet());
            }
        });
    }

    /**
     * This method authorizes and accepts clients
     */
    @Override
    public void authorize(){
        server.acceptClients();
    }

    /**
     * This method removes provided ID from set of sessions' IDs and updates list of client on index of provided user with false value.
     * @param ID ID of session to close.
     * @param user User, who wants to close connection
     */
    @Override
    public void cleanUpSessionForID(String ID, CredentialPacket user){
        removeId(ID);
        updateListOfClients(user, false);
        getServer().removeHandlerById(ID);
    }

    /**
     * This method returns reference to user founded by provided name, if not exists returns empty {@link CredentialPacket}
     * @param userName Key (user) to find
     * @return {@link CredentialPacket}
     */
    @Override
    public CredentialPacket findUserByName(String userName){
        return listOfClients.entrySet().stream().map(entry->entry.getKey()).filter(user-> user.getUserName().equals(userName)).findAny().orElse(new CredentialPacket("","",""));
    }

    /**
     * This method generates ID for new session.
     * @param accessor valid object
     * @return {@link String}
     */
    @Override
    public synchronized String generateID(Object accessor){
        if(! (accessor instanceof LimakWebApp.Utils.AbstractServerController)) return null;
        Random generator = new Random();
        StringBuilder stringBuilder = new StringBuilder();
        while(true) {
            for (int i = 0; i < getMaxSize(); ++i) {
                int chk = generator.nextInt() % 3;
                char c = chk == 2 ?
                        (char)(65 + generator.nextInt(26))
                        : (chk == 1 ?
                        (char)(97 + generator.nextInt(26))
                        : (char)(48 + generator.nextInt(10)));
                stringBuilder.append(c);
            }
            if(!checkIfIdIsValid(stringBuilder.toString())){
                return stringBuilder.toString();
            }
        }
    }

    private synchronized boolean checkIfIdIsValid(String idToPut){
        return ids.contains(idToPut);
    }

    private void fillRunTimeMap(){
        for(Map.Entry<CredentialPacket, Boolean> entry : listOfClients.entrySet()){
            if(!entry.getKey().getUserName().equals("Server")) {
                runTimeMapOfFileOwners.put(entry.getKey(), getListOfFilesForUser(entry.getKey()).stream().collect(Collectors.toCollection(ArrayList::new)));
            }
        }
    }

    private synchronized ConcurrentHashMap<String, ArrayList<CredentialPacket>> readContentsFromJson(int disk){
        ConcurrentHashMap<String, ArrayList<LinkedTreeMap<String,String>>> outMap = new ConcurrentHashMap<>();
        File root = new File(credentialPacket.getUserFolderPath());
        File subFolder = new File(root, Constants.getDirectories(this)[disk]);
        File fileToRead = new File(subFolder, Constants.getDirectoriesControlFile(this));
        if(fileToRead.length() < 1){
            return new ConcurrentHashMap<>();
        }
        Gson gson = new Gson();
        try(JsonReader reader = new JsonReader(new FileReader(fileToRead))){
            outMap = gson.fromJson(reader, outMap.getClass());
        }
        catch(IOException io) {
            setStatusText("Can't read file");
            StringBuilder stringBuilder = new StringBuilder();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream outStream = new PrintStream(outputStream);
            io.printStackTrace(outStream);
            stringBuilder.append(new Date())
                    .append(":\n")
                    .append("Can't read file: \n\t")
                    .append(fileToRead.getAbsolutePath()).append("\n")
                    .append(io.getMessage() + "\n")
                    .append(outStream.toString()).append("\n");
            addLog(Constants.LogType.ERROR, stringBuilder.toString());
        }
        ConcurrentHashMap<String, ArrayList<CredentialPacket>> rV = new ConcurrentHashMap<>();
        for(Map.Entry<String, ArrayList<LinkedTreeMap<String,String>>> node : outMap.entrySet()){
            ArrayList<CredentialPacket> list = node.getValue().stream().map(e -> new CredentialPacket(e.get("userEmail"), e.get("userName"), e.get("userFolderPath"))).collect(Collectors.toCollection(ArrayList::new));
            rV.put(node.getKey(), list);
        }
        return rV;
    }

    private synchronized void dumpContentsToJson(int disk){
        File root = new File(credentialPacket.getUserFolderPath());
        File subFolder = new File(root, Constants.getDirectories(this)[disk]);
        File fileToDump = new File(subFolder, Constants.getDirectoriesControlFile(this));
        Gson gson = new Gson();
        try(Writer writer= new FileWriter(fileToDump)){
            ConcurrentHashMap<String, ArrayList<CredentialPacket>> toDump = serverDiskMap.get(disk).getMap();
            gson.toJson(toDump, writer);
            setStatusText("File is saved successfully");
            StringBuilder builder = new StringBuilder();
            builder.append(new Date())
                    .append("\n").append("Saved properly a file:\n\t")
                    .append(fileToDump.getAbsolutePath() + "\n");
            addLog(Constants.LogType.SUCCESS, builder.toString());
        }
        catch (IOException io) {
            setStatusText("Can't save file");
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream outStream = new PrintStream(outputStream);
            io.printStackTrace(outStream);
            StringBuilder stringBuilder = new StringBuilder();
            stringBuilder.append(new Date())
                    .append(":\n")
                    .append("Can't save file: \n\t")
                    .append(fileToDump.getAbsolutePath()).append("\n")
                    .append(outStream.toString()).append("\n");
            addLog(Constants.LogType.ERROR, stringBuilder.toString());
        }

    }

    private void dumpListOfClients(){
        File root = new File(credentialPacket.getUserFolderPath());
        File listOfClientsFile = new File(root, Constants.getListOfClientsFileName(this));
        Gson gson= new Gson();
        try(Writer writer = new FileWriter(listOfClientsFile)) {
            CredentialPacket[] packets = listOfClients.keySet().toArray(new CredentialPacket[listOfClients.keySet().size()]);
            gson.toJson(packets, writer);
            setStatusText("File is saved successfully");
            StringBuilder builder = new StringBuilder();
            builder.append(new Date())
                    .append("\n").append("Saved properly a file:\n\t")
                    .append(listOfClientsFile.getAbsolutePath() + "\n");
            addLog(Constants.LogType.SUCCESS, builder.toString());
        }
        catch(IOException io) {
            setStatusText("Can't save file");
            StringBuilder stringBuilder = new StringBuilder();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream outStream = new PrintStream(outputStream);
            io.printStackTrace(outStream);
            stringBuilder.append(new Date())
                    .append(":\n")
                    .append("Can't save file: \n\t")
                    .append(listOfClientsFile.getAbsolutePath()).append("\n")
                    .append(io.getMessage() + "\n")
                    .append(outStream.toString()).append("\n");
            addLog(Constants.LogType.ERROR, stringBuilder.toString());
        }
    }

    private void readInitListOfClientsFromFile(){
        File root = new File(credentialPacket.getUserFolderPath());
        File listOfClientsFile = new File(root, Constants.getListOfClientsFileName(this));
        Gson gson = new Gson();
        try (JsonReader jsonReader = new JsonReader(new FileReader(listOfClientsFile))){
            CredentialPacket[] packets = gson.fromJson(jsonReader, CredentialPacket[].class);
            if(packets != null) {
                for (CredentialPacket packet : packets) {
                    listOfClients.put(packet, false);
                }
            }
        }
        catch(IOException|JsonParseException io) {
            setStatusText("Can't read or parse file");
            StringBuilder stringBuilder = new StringBuilder();
            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            PrintStream outStream = new PrintStream(outputStream);
            io.printStackTrace(outStream);
            stringBuilder.append(new Date())
                    .append(":\n")
                    .append("Can't read or parse file: \n\t")
                    .append(listOfClientsFile.getAbsolutePath()).append("\n")
                    .append(io.getMessage() + "\n")
                    .append(outStream.toString()).append("\n");
            addLog(Constants.LogType.ERROR, stringBuilder.toString());
        }
    }

    private void createServerDirectoriesIfNotExist(String[] directories){
        String stringPath = credentialPacket.getUserFolderPath();
        File rootFile = new File (stringPath);
        if(!rootFile.exists()){
            rootFile.mkdirs();
        }
        File[] listingDir = rootFile.listFiles();
        if(listingDir == null || ( listingDir.length < 5)) {
            for (String str : directories) {
               new File(stringPath + "\\" + str).mkdir();
            }
        }
        return;
    }

    private synchronized void removeId(String strId){
        synchronized (lock) {
            ids.remove(strId);
        }
    }

    private synchronized void checkIfAreFilesToSendForInactiveUsers() {
        ArrayList<CredentialPacket> inactiveUsersList = getInactiveListOfClients();
        Map<CredentialPacket, ArrayList<String>> filtered = runTimeMapOfFileOwners.entrySet().stream()
                .filter(entry -> inactiveUsersList.contains(entry.getKey()))
                .collect(Collectors.toConcurrentMap(entry -> entry.getKey(), entry -> entry.getValue()));
        filtered.forEach((key, value) -> {
            ArrayList<String> list = compareUserAndServerList(key, value).getValue().stream().map(file -> file.getName()).collect(Collectors.toCollection(ArrayList::new));
            if (list.size() > 0) {
                emailSession.sendEmail(key, false, list.toArray(new String[list.size()]));
            }
        });
    }

    private synchronized boolean addUserToFileOwners(String fileName, CredentialPacket user){
        boolean rV = false;
        synchronized (lock) {
            for (DiskMap disk : serverDiskMap) {
                if (disk.checkIfFileExists(fileName)) {
                    if (!user.isEmpty()) {
                        rV = disk.putOwnerToFile(fileName, user);
                    }
                    break;
                }
            }
        }
        return rV;
    }

    private void createImportantStartUpFilesIfNotExist(){
        File rootFile = new File (credentialPacket.getUserFolderPath());
        if(rootFile.exists()){
            File clientsFile = new File(rootFile, Constants.getListOfClientsFileName(this));
            if(!clientsFile.exists()){
                try(BufferedWriter ignored = new BufferedWriter(new FileWriter(clientsFile))){}
                catch (IOException io){
                    io.printStackTrace();
                }
            }
            for(File f : rootFile.listFiles()){
                if(f.isDirectory()){
                    File contentFile = new File(f, Constants.getDirectoriesControlFile(this));
                    if(!contentFile.exists()){
                        try(BufferedWriter ignored = new BufferedWriter(new FileWriter(contentFile))){}
                        catch(IOException io){
                            io.printStackTrace();
                        }
                    }
                }
            }
        }
    }

    private void displayTreeView() {
        String inputDirectoryLocation = credentialPacket.getUserFolderPath();
        String[] path = inputDirectoryLocation.split("\\\\");
        StringBuilder stringBuilder = new StringBuilder();
        TreeItem<File> rootItem = new TreeItem<>(new File(inputDirectoryLocation));
        serverTreeView.setShowRoot(true);
        serverTreeView.setCellFactory(callback-> {
        final Tooltip tooltip = new Tooltip();
        TreeCell<File> cell = new TreeCell<>() {
            @Override
            public void updateItem(File item, boolean empty) {
                StringFunctionalInterface stringFunctionalInterface = (StringBuilder stringBuilder) -> {
                    stringBuilder = new StringBuilder();
                    BasicFileAttributes fileAttributes;
                    try {
                        fileAttributes = Files.readAttributes(item.toPath(), BasicFileAttributes.class);
                        stringBuilder.append(fileAttributes.isDirectory() ? "Directory" : "File").append('\n');
                        stringBuilder.append("Path: ").append(item.getAbsolutePath()).append('\n');
                        stringBuilder.append("Creation time: ").append(new java.util.Date(fileAttributes.creationTime().toMillis()).toString()).append('\n');
                        stringBuilder.append("Last modification time: ").append(new java.util.Date(fileAttributes.lastModifiedTime().toMillis()).toString()).append('\n');
                        stringBuilder.append("Last access time: ").append(new java.util.Date(fileAttributes.lastAccessTime().toMillis()).toString()).append('\n');
                        long size = fileAttributes.isDirectory() ? Files.walk(item.toPath()).filter(p -> p.toFile().isFile()).mapToLong(p->p.toFile().length()).sum() :  fileAttributes.size();
                        stringBuilder.append("Size: ").append(computeDataStorageUnitAndValue(size)).append('\n');
                        stringBuilder.append("Author/current owner:").append(credentialPacket.getUserName());
                    } catch (IOException ignored){}
                    return stringBuilder.toString();
                };
                super.updateItem(item, empty);
                if (empty) {
                    setText(null);
                    setTooltip(null);
                } else if (getTreeItem() == rootItem) {
                    setText(path[path.length - 1]);
                    tooltip.setText(stringFunctionalInterface.getText(stringBuilder));
                    setTooltip(tooltip);
                } else {
                    setText(item.getName());
                    tooltip.setText(stringFunctionalInterface.getText(stringBuilder));
                    setTooltip(tooltip);
                }
            }
        };
            cell.setOnMouseClicked(e -> {
                if (e.getButton() == MouseButton.PRIMARY && e.getClickCount() == 2 && ! cell.isEmpty() && cell.getTreeItem() != rootItem) {
                    File file = cell.getItem();
                    try {
                        if (Desktop.isDesktopSupported()) {
                            Desktop desktop = Desktop.getDesktop();
                            desktop.open(file);
                        } else throw new IOException();
                    }
                    catch(IOException io) {
                        setStatusText("Can't open a file");
                        StringBuilder builder = new StringBuilder();
                        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
                        PrintStream outStream = new PrintStream(outputStream);
                        io.printStackTrace(outStream);
                        stringBuilder.append(new Date())
                                .append(":\n")
                                .append("Can't open file or Desktop is not supported class in your system: \n\t")
                                .append(file.getAbsolutePath()).append("\n")
                                .append(io.getMessage()).append("\n")
                                .append(outStream.toString()).append("\n");
                        addLog(Constants.LogType.ERROR, builder.toString());
                    }
                    setStatusText("File opened");
                    StringBuilder builder = new StringBuilder();
                    builder.append(new Date())
                            .append(":\n")
                            .append("Successfully opened a file: \n\t")
                            .append(file.getAbsolutePath()).append("\n");
                    addLog(Constants.LogType.SUCCESS, builder.toString());
                }
            });
            return cell ;
        });
        File fileInputDirectoryLocation = new File(inputDirectoryLocation);
        File[] fileList = fileInputDirectoryLocation.listFiles();
        for (File file : fileList) {
            createTree(file, rootItem);
        }
        rootItem.setExpanded(true);
        serverTreeView.setRoot(rootItem);
    }

    private void createTree(File file, TreeItem<File> parentTreeItem){
        if (file.isDirectory()) {
            TreeItem<File> treeItem = new TreeItem<>(file);
            parentTreeItem.getChildren().add(treeItem);
            for (File f : file.listFiles()) {
                createTree(f, treeItem);
            }
        } else {
            parentTreeItem.getChildren().add(new TreeItem<>(file));
        }
    }

    /**
     * Returns reference to disk of given index.
     * @param idx Indicates the number of disk we want to get access
     * @return {@link DiskMap}
     */
    @Override
    public synchronized DiskMap getDisk(int idx){
        return serverDiskMap.get(idx);
    }

    /**
     * This method returns server's credentials
     * @return CredentialPacket
     */
    @Override
    public CredentialPacket getCredentialPacket() {
        return credentialPacket;
    }

    /**
     * This method returns Server instance
     * @return Server
     */
    @Override
    public Server getServer() {
        return server;
    }

    /**
     * This method returns instance of email session
     * @return EmailUtil
     */
    @Override
    public EmailUtil getEmailSession() {
        return emailSession;
    }

    /**
     * This method returns full list of clients with current states
     * @return ListOfClients
     */
    @Override
    public ListOfClients getListOfClients(){
        return  listOfClients;
    }

    /**
     * This method clears root of tree of files.
     */
    @Override
    public void clearRoot() {
        Platform.runLater(()->serverTreeView.setRoot(null));
    }

    /**
     * This method refresh {@link TreeView}
     */
    @Override
    public void refreshTree() {
        clearRoot();
        displayTree();
    }

    /**
     * This method is a public accessor of {@link #displayTreeView()}.
     */
    @Override
    public void displayTree() {
        Platform.runLater(()->displayTreeView());
    }

    /**
     * Method sets current status of client application.
     * @param string status to set
     */
    @Override
    @FXML
    public void setStatusText(String string){
        Platform.runLater(()->serverStatusText.setText(string));
    }

    /**
     * Adds new log message to log pane with specified type and content.
     * @param logType type of message.
     * @param message content of message, usually contains a date of message and some basic content which indicates performed action.
     */
    @Override
    @FXML
    public void addLog(Constants.LogType logType, String message){
        Text textToAdd = new Text();
        textToAdd.setText(message);
        textToAdd.setWrappingWidth(390);
        switch(logType){
            case INFO:
                textToAdd.setFill(Color.BLUE);
                break;
            case ERROR:
                textToAdd.setFill(Color.RED);
                break;
            case SUCCESS:
                textToAdd.setFill(Color.GREEN);
                break;
        }
        Platform.runLater(()->{
            logContent.getChildren().add(textToAdd);
        });
    }
}
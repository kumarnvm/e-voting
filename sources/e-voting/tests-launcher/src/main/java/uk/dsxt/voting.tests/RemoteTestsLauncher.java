package uk.dsxt.voting.tests;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jcraft.jsch.*;
import lombok.extern.log4j.Log4j2;
import uk.dsxt.voting.common.utils.PropertiesHelper;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Paths;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.BiFunction;
import java.util.function.Function;

@Log4j2
public class RemoteTestsLauncher implements BaseTestsLauncher {

    private static final int BUFFER_SIZE = 4096;
    private static final String WORK_DIR = "/home/ubuntu/e-voting/";
    private JSch sshProvider = new JSch();
    private final int sshPort = 22;
    private final String user;
    private final String masterHost;
    private final int MASTER_NXT_PEER_PORT = 15000;
    private final int MASTER_NXT_API_PORT = 12000;
    private final String MASTER_NXT_PEER_ADDRESS;
    private final int MASTER_APP_PORT = 9000;
    private final String MAIN_ADDRESS;
    private final String MASTER_NODE_NAME = "master";
    private final Function<Integer, String> NODE_NAME = id -> id == 0 ? MASTER_NODE_NAME : String.format("node_%d", id);
    private final String PATH_TO_ISTALL_SCRIPT = "ssh/createNode.sh";
    
    private final String VOTING_DESCRIPTION = "voting.txt";
    private final String VOTING_NET_CONFIGURATION = "net.txt";
    private final String SCENARIO;
    private final String SCENARIO_HOME_DIR = "scenarios";
    private final String VOTING_XML_NAME = "voting.xml";
    private final String MESSAGES_NAME = "messages.txt";
    private final String MI_PARTICIPANTS_NAME = "mi_participants.xml";
    private final String CREDENTIALS_NAME = "credentials.json";
    private final String CLIENTS_NAME = "clients.json";
    
    private final BiFunction<String, String, String> ECHO_CMD = (data, path) -> String.format("/bin/echo -e \"%s\" > %s", data, path);
    
    private Map<Integer, NodeInfo> idToNodeInfo;
    private Map<String, Integer> hostToNodesCount;
    
    private final ObjectMapper mapper = new ObjectMapper();
    
    public static void main(String[] args) {
        try {
            log.info("Starting module {}...", MODULE_NAME);
            Properties properties = PropertiesHelper.loadProperties(MODULE_NAME);
            RemoteTestsLauncher instance = new RemoteTestsLauncher(properties);
            instance.run(properties);
        } catch (Exception e) {
            log.error("Module {} failed: ", MODULE_NAME, e.getMessage());
        }
    }
    
    RemoteTestsLauncher(Properties properties) throws Exception {
        user = properties.getProperty("vm.user");
        masterHost = properties.getProperty("vm.mainNode");
        sshProvider.addIdentity(properties.getProperty("vm.crtPath"));
        MASTER_NXT_PEER_ADDRESS = String.format("http://%s:%d", masterHost, MASTER_NXT_PEER_PORT); 
        MAIN_ADDRESS = properties.getProperty("master.address");
        SCENARIO = properties.getProperty("testing.type");
    }
    
    private void run(Properties properties) throws Exception {
        hostToNodesCount = new LinkedHashMap<>();
        idToNodeInfo = new HashMap<>();
        readConfig(hostToNodesCount, VOTING_NET_CONFIGURATION, str -> {
            String[] splited = str.split("=");
            if (splited.length == 2)
                return new AbstractMap.SimpleEntry<>(splited[0], Integer.parseInt(splited[1]));
            return null;
        });
        readConfig(idToNodeInfo, VOTING_DESCRIPTION, str -> {
            try {
                String[] splited = str.split("=");
                if (splited.length == 2)
                    return new AbstractMap.SimpleEntry<>(Integer.parseInt(splited[0]), mapper.readValue(splited[1], NodeInfo.class));
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        
        if (Boolean.parseBoolean(properties.getProperty("vm.updateBuild")))
            updateBuilds();
        if (Boolean.parseBoolean(properties.getProperty("vm.installOrUpdateNodes")))
            installOrUpdateNodes();
        if (Boolean.parseBoolean(properties.getProperty("vm.installOrUpdateScenario")))
            installOrUpdateScenario();
        if (Boolean.parseBoolean(properties.getProperty("vm.runScenario")))
            runScenario();
    }
    
    private <K, V> void readConfig(Map<K, V> map, String fileName, Function<String, Map.Entry<K, V>> parse) {
        String data = PropertiesHelper.getResourceString(Paths.get(SCENARIO_HOME_DIR, SCENARIO, fileName).toString());
        for (String str : data.split("\\r?\\n")) {
            Map.Entry<K, V> keyAndValue = parse.apply(str);
            if (keyAndValue != null)
                map.put(keyAndValue.getKey(), keyAndValue.getValue());
        }
    }

    private void installOrUpdateNode(Session session, int ownId, String ownerId, String privateKey, String mainNxtAddress,
                                      String accountPassphrase, boolean master, String ownerHost,
                                      String webHost, String directory, int portShift) throws Exception {
        final int currentWebPort = MASTER_APP_PORT + portShift;
        String pathToConfig = WORK_DIR + "build/" + directory + "/client.properties";
        String resourceString = PropertiesHelper.getResourceString(PATH_TO_ISTALL_SCRIPT);
        log.debug(makeCmd(session, resourceString.replace("$1", directory)));
        String backendConfig = makeCmd(session, String.format("cat %s", pathToConfig));
        log.debug(String.format("Initial backend config: %s%n", backendConfig));
        Map<String, String> overrides = new LinkedHashMap<>();
        overrides.put("client.isMain", Boolean.toString(master));
        overrides.put("voting.files", VOTING_XML_NAME);
        overrides.put("scheduled_messages.file_path", MESSAGES_NAME);
        overrides.put("participants_xml.file_path", MI_PARTICIPANTS_NAME);
        overrides.put("credentials.filepath", CREDENTIALS_NAME);
        overrides.put("clients.filepath", CLIENTS_NAME);
        overrides.put("client.webHost.port", Integer.toString(currentWebPort));
        overrides.put("owner.id", ownerId);
        overrides.put("owner.private_key", privateKey);
        overrides.put("parent.holder.url", ownerHost);
        overrides.put("mock.wallet", "false");
        overrides.put("mock.registries", "true");
        overrides.put("nxt.jar.path", "../libs/nxt.jar");
        overrides.put("nxt.properties.path", "./conf/nxt-default.properties");
        overrides.put("nxt.peerServerPort", Integer.toString(MASTER_NXT_PEER_PORT + portShift));
        overrides.put("nxt.apiServerPort", Integer.toString(MASTER_NXT_API_PORT + portShift));
        overrides.put("nxt.dbDir", String.format("./%s", DB_FOLDER));
        overrides.put("nxt.testDbDir", String.format("./%s", DB_FOLDER));
        overrides.put("nxt.defaultPeers", MASTER_NXT_PEER_ADDRESS);
        overrides.put("nxt.defaultTestnetPeers", MASTER_NXT_PEER_ADDRESS);
        overrides.put("nxt.isOffline", "false");
        overrides.put("nxt.isTestnet", "true");
        overrides.put("nxt.main.address", mainNxtAddress);
        overrides.put("nxt.account.passphrase", accountPassphrase);
        Map<String, String> original = new LinkedHashMap<>();
        for (String keyToValueStr : backendConfig.split(String.format("%n"))) {
            String[] keyToValue = keyToValueStr.split("=");
            if (keyToValue.length == 2)
                original.put(keyToValue[0], keyToValue[1]);
        }
        for (Map.Entry<String, String> keyToValue : overrides.entrySet()) {
            original.put(keyToValue.getKey(), keyToValue.getValue());
        }
        StringBuilder result = new StringBuilder();
        for (Map.Entry<String, String> keyToValue : original.entrySet()) {
            result.append(keyToValue.getKey());
            result.append("=");
            result.append(keyToValue.getValue());
            result.append(String.format("%n"));
        }
        log.debug(String.format("Result backend config: %s%n", backendConfig));
        makeCmd(session, ECHO_CMD.apply(result.toString(), pathToConfig));
        String pathToMasterFrontendConfig = WORK_DIR + "build/" + directory + "/gui-public/app/server-properties.js";
        String frontendConfig = makeCmd(session, String.format("cat %s", pathToMasterFrontendConfig));
        log.debug(String.format("Original frontend config: %s%n", frontendConfig));
        frontendConfig = frontendConfig.replaceAll("\"serverUrl\": \".*\",", String.format("\"serverUrl\": \"%s\"", String.format("http://%s:%d/", webHost, currentWebPort)));
        frontendConfig = frontendConfig.replaceAll("\"serverPort\": .*,", String.format("\"serverPort\": %s", currentWebPort));
        frontendConfig = frontendConfig.replaceAll("\"pathToApi\": \".*\",", "\"pathToApi\": \"api\"");
        frontendConfig = frontendConfig.replaceAll("\"readPortFromUrl\": .*\n", "\"readPortFromUrl\": true\n");
        log.debug(String.format("Result frontend config: %s%n", frontendConfig));
        makeCmd(session, ECHO_CMD.apply(frontendConfig, pathToMasterFrontendConfig));


        String holderApi = String.format("http://%s:%d/holderAPI", webHost, currentWebPort);
        NodeInfo nodeInfo = idToNodeInfo.get(ownId);
        if (nodeInfo == null) {
            log.warn("Node with id {} was not found.", ownId);
        } else
            nodeInfo.setHolderAPI(holderApi);
    }

    private void updateBuilds() throws Exception {
        for (String host : hostToNodesCount.keySet())
            log.debug(makeCmd(getSession(host), String.format("cd %s; ./update.sh", WORK_DIR)));
    }

    private void installOrUpdateNodes() throws Exception {        
        iterateByAllNodes((session, currentNodeId) -> {
            NodeInfo nodeInfo = idToNodeInfo.get(currentNodeId);
            NodeInfo ownerNodeInfo = idToNodeInfo.get(nodeInfo.getOwnerId());
            String currentHost = session.getHost();
            boolean master = currentHost.equals(masterHost) && currentNodeId == 0;
            try {
                installOrUpdateNode(
                    session,
                    currentNodeId,
                    master ? "00" : Integer.toString(nodeInfo.getId()),
                    nodeInfo.getPrivateKey(),
                    MAIN_ADDRESS,
                    nodeInfo.getNxtPassword(),
                    master,
                    ownerNodeInfo.getHolderAPI(),
                    currentHost,
                    NODE_NAME.apply(currentNodeId),
                    currentNodeId);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void installOrUpdateScenario() throws Exception {
        iterateByAllNodes((session, currentNodeId) -> {
            try {
                uploadFile(session, currentNodeId, VOTING_XML_NAME);
                uploadFile(session, currentNodeId, MESSAGES_NAME);
                uploadFile(session, currentNodeId, MI_PARTICIPANTS_NAME);
                uploadFile(session, currentNodeId, CREDENTIALS_NAME);
                uploadFile(session, currentNodeId, CLIENTS_NAME);                
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }
    
    private void uploadFile(Session session, int currentNodeId, String fileName) throws Exception {
        String data = PropertiesHelper.getResourceString(Paths.get(SCENARIO_HOME_DIR, SCENARIO, Integer.toString(currentNodeId), fileName).toString());
        makeCmd(session, ECHO_CMD.apply(data, Paths.get(WORK_DIR, "build", NODE_NAME.apply(currentNodeId), fileName).toString()));
    }

    private void runScenario() throws Exception {
        iterateByAllNodes((session, currentNodeId) -> {
            try {
                makeCmd(session, String.format("cd %sbuild/%s/; rm -r ./%s*; java -jar client.jar", WORK_DIR, NODE_NAME.apply(currentNodeId), DB_FOLDER));
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void iterateByAllNodes(BiConsumer<Session, Integer> action) throws Exception {
        int counter = 0;
        for (Map.Entry<String, Integer> hostWithNodesCount : hostToNodesCount.entrySet()) {
            String currentHost = hostWithNodesCount.getKey();
            Session session = getSession(currentHost);
            int getCount = hostWithNodesCount.getValue();
            for (int i = 0; i < getCount; i++)
                action.accept(session, counter++);
        }
    }
    
    public String makeCmd(Session s, String cmd) throws Exception {
        ChannelExec exec = (ChannelExec)s.openChannel("exec");
        exec.setCommand(cmd);
        exec.connect();
        byte[] output = readData(exec, exec.getInputStream());
        byte[] error = readData(exec, exec.getErrStream());
        int exitStatus = exec.getExitStatus();
        byte[] resultStream = exitStatus == 0 ? output : error;
        String result = new String(resultStream, StandardCharsets.UTF_8);
        exec.disconnect();
        return exitStatus == 0 ? result : String.format("Exit with code: %d. Output: %s", exitStatus, result);
    }
    
    private byte[] readData(ChannelExec exec, InputStream stream) throws Exception {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        while (!exec.isEOF() || stream.available() > 0) {
            while (stream.available() > 0) {
                byte[] tmp = new byte[BUFFER_SIZE];
                int i = stream.read(tmp, 0, BUFFER_SIZE);
                if (i < 0) {
                    break;
                }
                buffer.write(tmp, 0, i);
            }
            Thread.sleep(100);
        }
        return buffer.toByteArray();
    }
    
    public Session getSession(String host) throws Exception {
        Session session = sshProvider.getSession(user, host, sshPort);
        session.setConfig("StrictHostKeyChecking", "no");
        session.connect();
        return session;
    }
}

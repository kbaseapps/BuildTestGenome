package buildtestgenome.test;

import genomeannotationapi.FeatureData;
import genomeannotationapi.GenomeAnnotationAPIClient;
import genomeannotationapi.GenomeAnnotationData;
import genomeannotationapi.GetCombinedDataParams;
import genomeannotationapi.ProteinData;

import java.io.File;
import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import junit.framework.Assert;

import org.ini4j.Ini;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import buildtestgenome.BuildTestGenomeServer;
import buildtestgenome.PrepareTestGenomeFromProteinsParams;
import us.kbase.auth.AuthConfig;
import us.kbase.auth.AuthToken;
import us.kbase.auth.ConfigurableAuthService;
import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.RpcContext;
import us.kbase.common.service.UObject;
import us.kbase.common.utils.FastaReader;
import us.kbase.workspace.CreateWorkspaceParams;
import us.kbase.workspace.ProvenanceAction;
import us.kbase.workspace.WorkspaceClient;
import us.kbase.workspace.WorkspaceIdentity;

public class BuildTestGenomeServerTest {
    private static AuthToken token = null;
    private static Map<String, String> config = null;
    private static WorkspaceClient wsClient = null;
    private static String wsName = null;
    private static BuildTestGenomeServer impl = null;
    
    @BeforeClass
    public static void init() throws Exception {
        String configFilePath = System.getenv("KB_DEPLOYMENT_CONFIG");
        File deploy = new File(configFilePath);
        Ini ini = new Ini(deploy);
        config = ini.get("BuildTestGenome");
        // Token validation
        String authUrl = config.get("auth-service-url");
        String authUrlInsecure = config.get("auth-service-url-allow-insecure");
        ConfigurableAuthService authService = new ConfigurableAuthService(
                new AuthConfig().withKBaseAuthServerURL(new URL(authUrl))
                .withAllowInsecureURLs("true".equals(authUrlInsecure)));
        token = authService.validateToken(System.getenv("KB_AUTH_TOKEN"));

        wsClient = new WorkspaceClient(new URL(config.get("workspace-url")), token);
        wsClient.setIsInsecureHttpConnectionAllowed(true);
        // These lines are necessary because we don't want to start linux syslog bridge service
        JsonServerSyslog.setStaticUseSyslog(false);
        JsonServerSyslog.setStaticMlogFile(new File(config.get("scratch"), "test.log").getAbsolutePath());
        impl = new BuildTestGenomeServer();
    }
    
    private static String getWsName() throws Exception {
        if (wsName == null) {
            long suffix = System.currentTimeMillis();
            wsName = "test_BuildTestGenome_" + suffix;
            wsClient.createWorkspace(new CreateWorkspaceParams().withWorkspace(wsName));
        }
        return wsName;
    }
    
    private static RpcContext getContext() {
        return new RpcContext().withProvenance(Arrays.asList(new ProvenanceAction()
            .withService("BuildTestGenome").withMethod("please_never_use_it_in_production")
            .withMethodParams(new ArrayList<UObject>())));
    }
    
    @AfterClass
    public static void cleanup() {
        if (wsName != null) {
            try {
                wsClient.deleteWorkspace(new WorkspaceIdentity().withWorkspace(wsName));
                System.out.println("Test workspace was deleted");
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }
    
    @Test
    public void testPrepareTestGenomeFromProteins() throws Exception {
        FastaReader fr = new FastaReader(new File(
                "/kb/module/test/data/Shewanella_ANA_3_uid58347.fasta"));
        Map<String, String> proteinIdToSeq = fr.readAll();
        fr.close();
        Map<String, String> proteinIdToSequence = new LinkedHashMap<>();
        proteinIdToSequence.putAll(proteinIdToSeq);
        int proteinCount = proteinIdToSequence.size();
        String genomeRef = impl.prepareTestGenomeFromProteins(
                new PrepareTestGenomeFromProteinsParams().withOutputWorkspaceName(getWsName())
                .withOutputObjectName("genome.1").withGenomeName("Shewanella ANA 3 uid58347")
                .withProteinIdToSequence(proteinIdToSequence), token, getContext());
        GenomeAnnotationAPIClient gaapi = new GenomeAnnotationAPIClient(
                new URL(System.getenv("SDK_CALLBACK_URL")), token);
        gaapi.setIsInsecureHttpConnectionAllowed(true);
        GenomeAnnotationData gad = gaapi.getCombinedData(
                new GetCombinedDataParams().withRef(genomeRef).withExcludeGenes(1L)
                .withExcludeCdsIdsByGeneId(1L));
        Assert.assertEquals(proteinCount, gad.getFeatureByIdByType().get(gad.getCdsType()).size());
        Assert.assertEquals(proteinCount, gad.getProteinByCdsId().size());
        Map<String, FeatureData> cdss = gad.getFeatureByIdByType().get(gad.getCdsType());
        Map<String, ProteinData> cdsIdToProt = gad.getProteinByCdsId();
        int cdssFound = 0;
        for (String cdsId : cdss.keySet()) {
            ProteinData prot = cdsIdToProt.get(cdsId);
            String gaSeq = prot.getProteinAminoAcidSequence();
            if (proteinIdToSequence.containsKey(cdsId)) {
                String origSeq = proteinIdToSequence.get(cdsId);
                boolean match = gaSeq.equals(origSeq);
                if (match)
                    cdssFound++;
            }
        }
        Assert.assertEquals(proteinCount, cdssFound);
    }
}

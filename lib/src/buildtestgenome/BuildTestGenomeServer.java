package buildtestgenome;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.Map;
import us.kbase.auth.AuthToken;
import us.kbase.common.service.JsonServerMethod;
import us.kbase.common.service.JsonServerServlet;
import us.kbase.common.service.JsonServerSyslog;
import us.kbase.common.service.RpcContext;

//BEGIN_HEADER
import genomeannotationapi.GenomeAnnotationAPIClient;
import genomeannotationapi.SaveOneGenomeParamsV1;

import java.net.URL;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;

import kbasegenomes.Feature;
import kbasegenomes.Genome;

import us.kbase.common.service.Tuple4;
import us.kbase.common.service.Tuple11;

import workspace.ProvenanceAction;
//END_HEADER


/**
 * <p>Original spec-file module name: BuildTestGenome</p>
 * <pre>
 * A KBase module: BuildTestGenome
 * It's useful for preparation of small Genome objects in tests of
 * methods dealing with protein translations in CDSs only.
 * </pre>
 */
public class BuildTestGenomeServer extends JsonServerServlet {
    private static final long serialVersionUID = 1L;
    private static final String version = "0.0.1";
    private static final String gitUrl = "";
    private static final String gitCommitHash = "";

    //BEGIN_CLASS_HEADER
    
    public static String getRefFromObjectInfo(Tuple11<Long, String, String, String, 
            Long, String, Long, String, String, Long, Map<String,String>> info) {
        return info.getE7() + "/" + info.getE1() + "/" + info.getE5();
    }
    //END_CLASS_HEADER

    public BuildTestGenomeServer() throws Exception {
        super("BuildTestGenome");
        //BEGIN_CONSTRUCTOR
        //END_CONSTRUCTOR
    }

    /**
     * <p>Original spec-file function name: prepare_test_genome_from_proteins</p>
     * <pre>
     * </pre>
     * @param   params   instance of type {@link buildtestgenome.PrepareTestGenomeFromProteinsParams PrepareTestGenomeFromProteinsParams}
     * @return   parameter "genome_ref" of String
     */
    @JsonServerMethod(rpc = "BuildTestGenome.prepare_test_genome_from_proteins", async=true)
    public String prepareTestGenomeFromProteins(PrepareTestGenomeFromProteinsParams params, AuthToken authPart, RpcContext jsonRpcContext) throws Exception {
        String returnVal = null;
        //BEGIN prepare_test_genome_from_proteins
        try {
            List<Feature> features = new ArrayList<Feature>();
            List<String> proteinIds = new ArrayList<>(params.getProteinIdToSequence().keySet());
            String contigId = "contig1";
            long featureStart = 1;
            for (int genePos = 0; genePos < proteinIds.size(); genePos++) {
                String proteinId = proteinIds.get(genePos);
                String proteinSeq = params.getProteinIdToSequence().get(proteinId);
                long featureLength = proteinSeq.length() * 3;
                features.add(new Feature().withId(proteinId)
                        .withProteinTranslation(proteinSeq)
                        .withType("CDS").withLocation(Arrays.asList(
                                new Tuple4<String, Long, String, Long>().withE1(contigId)
                                .withE2(featureStart).withE3("+").withE4(featureLength))));
                featureStart += featureLength;
            }
            //char[] contigSeqChars = new char[(int)featureStart + 5];
            //Arrays.fill(contigSeqChars, 'a');
            Genome genome = new Genome().withScientificName(params.getGenomeName())
                    .withFeatures(features).withId(params.getGenomeName())
                    .withDomain("Bacteria").withGeneticCode(11L);
            GenomeAnnotationAPIClient gaa = new GenomeAnnotationAPIClient(
                    new URL(System.getenv("SDK_CALLBACK_URL")), authPart);
            gaa.setIsInsecureHttpConnectionAllowed(true);
            returnVal = getRefFromObjectInfo(gaa.saveOneGenomeV1(
                    new SaveOneGenomeParamsV1().withData(genome)
                    .withName(params.getOutputObjectName())
                    .withWorkspace(params.getOutputWorkspaceName())
                    .withProvenance(new ArrayList<ProvenanceAction>())).getInfo());
        } finally {
            // nothing to clean up
        }
        //END prepare_test_genome_from_proteins
        return returnVal;
    }
    @JsonServerMethod(rpc = "BuildTestGenome.status")
    public Map<String, Object> status() {
        Map<String, Object> returnVal = null;
        //BEGIN_STATUS
        returnVal = new LinkedHashMap<String, Object>();
        returnVal.put("state", "OK");
        returnVal.put("message", "");
        returnVal.put("version", version);
        returnVal.put("git_url", gitUrl);
        returnVal.put("git_commit_hash", gitCommitHash);
        //END_STATUS
        return returnVal;
    }

    public static void main(String[] args) throws Exception {
        if (args.length == 1) {
            new BuildTestGenomeServer().startupServer(Integer.parseInt(args[0]));
        } else if (args.length == 3) {
            JsonServerSyslog.setStaticUseSyslog(false);
            JsonServerSyslog.setStaticMlogFile(args[1] + ".log");
            new BuildTestGenomeServer().processRpcCall(new File(args[0]), new File(args[1]), args[2]);
        } else {
            System.out.println("Usage: <program> <server_port>");
            System.out.println("   or: <program> <context_json_file> <output_json_file> <token>");
            return;
        }
    }
}

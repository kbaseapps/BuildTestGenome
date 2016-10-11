/*
A KBase module: BuildTestGenome
It's useful for preparation of small Genome objects in tests of
methods dealing with protein translations in CDSs only.
*/

module BuildTestGenome {

    typedef structure {
        string output_workspace_name;
        string output_object_name;
        string genome_name;
        mapping<string,string> protein_id_to_sequence;
    } PrepareTestGenomeFromProteinsParams;

    funcdef prepare_test_genome_from_proteins(
        PrepareTestGenomeFromProteinsParams params) 
        returns (string genome_ref) authentication required;

};

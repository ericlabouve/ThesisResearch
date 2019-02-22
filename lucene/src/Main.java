import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.*;

import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.RAMDirectory;
import org.tartarus.snowball.ext.PorterStemmer;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;

public class Main {

    String abs_path = "C:\\Users\\Eric\\Documents\\Thesis\\src\\Term-Proximity-Research\\datasets";
    String lisa_path = "C:\\Users\\Eric\\Documents\\Thesis\\src\\Term-Proximity-Research\\datasets\\lisa\\lisa_clean.all";
    String lisa_notitle_path = "C:\\Users\\Eric\\Documents\\Thesis\\src\\Term-Proximity-Research\\datasets\\lisa\\lisa_clean_notitle.all";
    String lisa_queries_path = "C:\\Users\\Eric\\Documents\\Thesis\\src\\Term-Proximity-Research\\datasets\\lisa\\LISA.QUE";

    // map {Query id : [Doc Ids]}
    HashMap<Integer, ArrayList<Integer>> results = new HashMap<>();

    PorterStemmer stemmer = new PorterStemmer();
    private Set<String> noiseWordsSet = null;
    private String noiseWordArray[] = {"a", "about", "above", "all", "along",
            "also", "although", "am", "an", "and", "any", "are", "aren't", "as", "at",
            "be", "because", "been", "but", "by", "can", "cannot", "could", "couldn't",
            "did", "didn't", "do", "does", "doesn't", "e.g.", "either", "etc", "etc.",
            "even", "ever", "enough", "for", "from", "further", "get", "gets", "got", "had", "have",
            "hardly", "has", "hasn't", "having", "he", "hence", "her", "here",
            "hereby", "herein", "hereof", "hereon", "hereto", "herewith", "him",
            "his", "how", "however", "i", "i.e.", "if", "in", "into", "it", "it's", "its",
            "me", "more", "most", "mr", "my", "near", "nor", "now", "no", "not", "or", "on", "of", "onto",
            "other", "our", "out", "over", "really", "said", "same", "she",
            "should", "shouldn't", "since", "so", "some", "such",
            "than", "that", "the", "their", "them", "then", "there", "thereby",
            "therefore", "therefrom", "therein", "thereof", "thereon", "thereto",
            "therewith", "these", "they", "this", "those", "through", "thus", "to",
            "too", "under", "until", "unto", "upon", "us", "very", "was", "wasn't",
            "we", "were", "what", "when", "where", "whereby", "wherein", "whether",
            "which", "while", "who", "whom", "whose", "why", "with", "without",
            "would", "you", "your", "yours", "yes"};

    public static void main(String[] args)  {
        Main main = new Main();
        try {
            main.run();
        }
        catch (ParseException e) {
            e.printStackTrace();
        }
        catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void run() throws IOException, ParseException {
        // 0. Specify the analyzer for tokenizing text.
        //    The same analyzer should be used for indexing and searching
        StandardAnalyzer analyzer = new StandardAnalyzer();

        // 1. create the index
        Directory index = new RAMDirectory();

        IndexWriterConfig config = new IndexWriterConfig(analyzer);

        IndexWriter w = new IndexWriter(index, config);


//        ArrayList<MyDocument> corpusDocs = getDocuments(lisa_path, true);
//        ArrayList<MyDocument> corpusDocs = getDocuments(lisa_path, false);
//        ArrayList<MyDocument> corpusDocs = getDocuments(lisa_notitle_path, true);
        ArrayList<MyDocument> corpusDocs = getDocuments(lisa_notitle_path, false);


        for (MyDocument doc: corpusDocs) {
            addDoc(w, doc);
        }
        w.close();

//        ArrayList<MyQuery> corpusQueries = getQueries(lisa_queries_path, true);
        ArrayList<MyQuery> corpusQueries = getQueries(lisa_queries_path, false);


        IndexReader reader = DirectoryReader.open(index);
        // Execute every query
        for (MyQuery qu : corpusQueries) {
            // 2. query
            String querystr = qu.body;
            results.put(qu.id, new ArrayList<Integer>());
            // Compute the query
            Query q = new QueryParser("body", analyzer).parse(querystr);

            // 3. search
            int hitsPerPage = corpusDocs.size();
            IndexSearcher searcher = new IndexSearcher(reader);
            TopDocs docs = searcher.search(q, hitsPerPage);
            ScoreDoc[] hits = docs.scoreDocs;

            // 4. save results
            System.out.println("Found " + hits.length + " hits.");
            for(int i=0;i<hits.length;++i) {
                int docId = hits[i].doc;
                Document d = searcher.doc(docId);

                results.get(qu.id).add(Integer.parseInt(d.get("id")));

//                System.out.println((i + 1) + ". " + "\t" + "ID=" + d.get("id") + ": " + d.get("body"));
            }
        }

        // Save results in JSON object so we can easily load it in the Python project
        JSONObject jo = new JSONObject();
        jo.putAll(results);
        System.out.println(jo);

        // reader can only be closed when there
        // is no need to access the documents any more.
        reader.close();
    }


    private void addDoc(IndexWriter w, MyDocument d) throws IOException {
        Document doc = new Document();
        doc.add(new TextField("id", Integer.toString(d.id), Field.Store.YES));
        doc.add(new TextField("body", d.body, Field.Store.YES));
        w.addDocument(doc);
    }

    /**
     * If edit is true,
     * Convert everything to lower case
     * Remove stop words
     * Stem using Porter Stemmer.
     *
     * When edit is false, we make no edits to the document
     */
    private ArrayList<MyDocument> getDocuments(String filePath, boolean edit) {
        int minWordLength = 2;
        ArrayList<MyDocument> docs = new ArrayList<>();
        StringBuilder body = new StringBuilder();

        try(BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            int curDocId = -1; // Current MyDocument ID
            int curDocIdx = 0; // Current MyDocument Index
            boolean insideW = false;
            String line = br.readLine();
            // Continue until end of file is reached
            while (line != null) {
                // Check if we have reached a new document
                if (line.contains(".I")) {
                    if (curDocId != -1) {
                        docs.add(new MyDocument(curDocId, body.toString()));
                    }
                    // The current MyDocument ID is the number after .I
                    curDocId = Integer.valueOf(line.replace(".I", "").replaceAll("\\s+",""));
                    body = new StringBuilder();
                    insideW = false;
                }
                // If next line will indicate that we are parsing the text body
                else if (line.contains(".W")) {
                    insideW = true;
                    curDocIdx = 0; // Reset for next document
                }
                // If we are about to parse the text body
                else if (insideW) {
                    // Modify the document by converting words to lower case, stem, and remove stop words
                    if (edit) {
                        // Split into individual words
                        String[] tokens = line.split("[^a-zA-Z,.]+");
                        for (int i = 0; i < tokens.length; i++) {
                            String term = tokens[i].toLowerCase();
                            // Store words that are appropriate length
                            if (term.length() >= minWordLength) {
                                // If the current word is not a noise word
                                if (!isNoiseWord(term)) {
                                    term = stem(term);
                                    body.append(term + " ");
                                    curDocIdx++;
                                }
                            }
                        }
                    }
                    // Edit is false, so we simply append the line to the body
                    else {
                        body.append(line + " ");
                    }
                }
                line = br.readLine();
            }
            docs.add(new MyDocument(curDocId, body.toString()));
        } catch (java.io.FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
        }
        return docs;
    }


    /**
     * If edit is true,
     * Convert everything to lower case
     * Remove stop words
     * Stem using Porter Stemmer.
     *
     * When edit is false, we make no edits to the document
     */
    public ArrayList<MyQuery> getQueries(String filePath, boolean edit) {
        int minWordLength = 2;
        ArrayList<MyQuery> queries = new ArrayList<>();
        StringBuilder body = new StringBuilder();

        try(BufferedReader br = new BufferedReader(new FileReader(filePath))) {
            int curQuId = 0; //  Current Query ID
            int curQuIdx = 0; // Current Document Index
            boolean insideW = false;
            String line = br.readLine();
            // Continue until end of file is reached
            while (line != null) {
                // Check if we have reached a new document
                if (line.contains(".I")) {
                    if (curQuId != 0) {
                        queries.add(new MyQuery(curQuId, body.toString()));
                    }
                    // The current Document is the number after .I
                    curQuId++;
                    body = new StringBuilder();
                    insideW = false;
                }
                else if (line.contains(".W")) {
                    insideW = true;
                    curQuIdx = 0; // Reset for next query
                }
                else if (insideW) {
                    if (edit) {
                        String[] tokens = line.split("[^a-zA-Z,.]+");
                        for (int i = 0; i < tokens.length; i++) {
                            String term = tokens[i].toLowerCase();
                            // Store words that are appropriate length
                            if (term.length() >= minWordLength) {
                                // If noise words are off and the current word is not a noise word
                                if (!isNoiseWord(tokens[i])) {
                                    term = stem(term);
                                    body.append(term + " ");
                                    curQuIdx++;
                                }
                            }
                        }
                    }
                    else {
                        body.append(line + " ");
                    }
                }
                line = br.readLine();
            }
            queries.add(new MyQuery(curQuId, body.toString()));
        } catch (java.io.FileNotFoundException ex) {
            ex.printStackTrace();
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
        }
        return queries;
    }



    private boolean isNoiseWord(String word) {
        // Lazy loading
        if (noiseWordsSet == null) {
            noiseWordsSet = new HashSet<String>(Arrays.asList(noiseWordArray));
        }
        return noiseWordsSet.contains(word);
    }

    private String stem(String input) {
        stemmer.setCurrent(input);
        stemmer.stem();
        return stemmer.getCurrent();
    }

    class MyDocument {
        int id;
        String body;
        public MyDocument(int id, String body) {
            this.id = id;
            this.body = body;
        }
        @Override
        public String toString() {
            return Integer.toString(id) + ": " + body;
        }
    }

    class MyQuery extends MyDocument {
        public MyQuery (int id, String body) {
            super(id, body);
        }
    }
}

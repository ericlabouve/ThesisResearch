import org.apache.solr.client.solrj.SolrQuery;
import org.apache.solr.client.solrj.SolrServerException;
import org.apache.solr.client.solrj.beans.Field;
import org.apache.solr.client.solrj.impl.HttpSolrClient;
import org.apache.solr.client.solrj.impl.XMLResponseParser;
import org.apache.solr.client.solrj.response.QueryResponse;
import org.apache.solr.common.SolrDocument;
import org.apache.solr.common.SolrDocumentList;
import org.apache.solr.common.SolrInputDocument;
import org.json.simple.JSONObject;


import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;

import static org.junit.Assert.assertEquals;

public class Main {

    String lisa_path = "C:\\Users\\Eric\\Documents\\Thesis\\src\\Term-Proximity-Research\\datasets\\lisa\\lisa_clean.all";
    String lisa_notitle_path = "C:\\Users\\Eric\\Documents\\Thesis\\src\\Term-Proximity-Research\\datasets\\lisa\\lisa_clean_notitle.all";
    String lisa_queries_path = "C:\\Users\\Eric\\Documents\\Thesis\\src\\Term-Proximity-Research\\datasets\\lisa\\LISA.QUE";

    // map {Query id : [Doc Ids]}
    HashMap<Integer, ArrayList<Integer>> results = new HashMap<>();

    public static void main(String[] args) throws IOException, SolrServerException {
        Main m = new Main();
        m.go();
    }

    public void go() throws IOException, SolrServerException {
        // Define the host's location
//        String urlString = "http://localhost:8983/solr/big";
        String urlString = "http://localhost:8983/solr/lisa";
        HttpSolrClient solr = new HttpSolrClient.Builder(urlString).build();
        // Redefine parser to be XML (default is binary)
        solr.setParser(new XMLResponseParser());

        // Get all the Lisa documents
//        ArrayList<DocumentBean> corpusDocs = getDocuments(lisa_path); // Stored in core=big
        ArrayList<DocumentBean> corpusDocs = getDocuments(lisa_notitle_path); // Stored in core=lisa

        // Get all the Lisa queries
        ArrayList<QueryBean> corpusQueries = getQueries(lisa_queries_path);

        // Add all the documents to our database
        //addDocsToDatabase(solr, corpusDocs);

        // Execute each query and store the returned document ids in the results object
        for (QueryBean qBean : corpusQueries) {
            int qID = qBean.getId();
            String qBody = qBean.getBody();
            qBody = qBody.replaceAll(":", "");
            results.put(qID, new ArrayList<Integer>());

            // Call the query
            SolrQuery query = new SolrQuery();
            query.setQuery("body:("+qBody+")");
            query.setRequestHandler("body");
            query.setRows(corpusDocs.size());


            QueryResponse resp = solr.query(query);
            SolrDocumentList docList = resp.getResults();
            // For each returned document from this query
            for (SolrDocument doc : docList) {
                int docID = Integer.parseInt((String)doc.getFieldValue("id"));
                results.get(qID).add(docID);
            }
        }

        // Save results in JSON object so we can easily load it in the Python project
        JSONObject jo = new JSONObject();
        jo.putAll(results);
        System.out.println(jo);



        // Add a document to our collection
//        SolrInputDocument document = new SolrInputDocument();
//        document.addField("id", "123456");
//        document.addField("name", "Kenmore Dishwasher");
//        document.addField("price", "599.99");
//        solr.add(document);
//        solr.commit();

        // Another way to add a document to our collection using Beans
//        solr.addBean( new ProductBean("888", "Apple iPhone 6s", "299.99") );
//        solr.commit();

        // Query the database to fetch all documents matching XXX:XXX
//        SolrQuery query = new SolrQuery();
//        query.set("q", "price:599.99");
//
//        QueryResponse response = solr.query(query);
//        SolrDocumentList docList = response.getResults();
//        assertEquals(docList.getNumFound(), 1);
//
//        for (SolrDocument doc : docList) {
//            assertEquals((String) doc.getFieldValue("id"), "123456");
//            assertEquals((Double) doc.getFieldValue("price"), (Double) 599.99);
//        }

        // Another way to query solr
//        SolrDocument doc = solr.getById("123456");
//        assertEquals((String) doc.getFieldValue("name"), "Kenmore Dishwasher");
//        assertEquals((Double) doc.getFieldValue("price"), (Double) 599.99);

        System.out.println("Done");
    }

    private void addDocsToDatabase(HttpSolrClient solr, ArrayList<DocumentBean> corpusDocs) throws IOException, SolrServerException {
        for (DocumentBean docBean : corpusDocs) {
            solr.addBean(docBean);
            solr.commit();
        }
    }

    public class DocumentBean {
        @Field("id")
        int id;
        @Field("body")
        String body;

        public DocumentBean(int id, String body) {
            this.id = id;
            this.body = body;
        }

        public int getId() {
            return id;
        }

        @Field("id")
        public void setId(int id) {
            this.id = id;
        }

        public String getBody() {
            return body;
        }

        @Field("body")
        public void setBody(String body) {
            this.body = body;
        }
    }

    public class QueryBean {
        int id;
        String body;

        public QueryBean(int id, String body) {
            this.id = id;
            this.body = body;
        }

        public int getId() {
            return id;
        }

        @Field("id")
        public void setId(int id) {
            this.id = id;
        }

        public String getBody() {
            return body;
        }

        @Field("body")
        public void setBody(String body) {
            this.body = body;
        }
    }


    private ArrayList<DocumentBean> getDocuments(String filePath) {
        ArrayList<DocumentBean> docs = new ArrayList<>();
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
                        docs.add(new DocumentBean(curDocId, body.toString()));
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
                    body.append(line + " ");
                }
                line = br.readLine();
            }
            docs.add(new DocumentBean(curDocId, body.toString()));
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
        }
        return docs;
    }



    public ArrayList<QueryBean> getQueries(String filePath) {
        ArrayList<QueryBean> queries = new ArrayList<>();
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
                        queries.add(new QueryBean(curQuId, body.toString()));
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
                    body.append(line + " ");
                }
                line = br.readLine();
            }
            queries.add(new QueryBean(curQuId, body.toString()));
        } catch (java.io.IOException ex) {
            ex.printStackTrace();
        }
        return queries;
    }



//    public class ProductBean {
//
//        String id;
//        String name;
//        String price;
//
//        public ProductBean(String id, String name, String price) {
//            this.id = id;
//            this.name = name;
//            this.price = price;
//        }
//
//        @Field("id")
//        protected void setId(String id) {
//            this.id = id;
//        }
//
//        @Field("name")
//        protected void setName(String name) {
//            this.name = name;
//        }
//
//        @Field("price")
//        protected void setPrice(String price) {
//            this.price = price;
//        }
//
//        public String getId() {
//            return id;
//        }
//
//        public String getName() {
//            return name;
//        }
//
//        public String getPrice() {
//            return price;
//        }
//
//        // getters and constructor omitted for space
//    }
}

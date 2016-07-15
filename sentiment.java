import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.net.URL;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;
import java.util.TimeZone;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

import weka.filters.Filter;
import weka.filters.unsupervised.attribute.Add;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToWordVector;
import weka.datagenerators.DataGenerator;
import weka.datagenerators.classifiers.classification.RDG1;

import org.json.simple.JSONArray;
import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;
import org.json.simple.parser.ParseException;

import twitter4j.Twitter;
import twitter4j.TwitterException;
import twitter4j.TwitterFactory;
import twitter4j.auth.AccessToken;
import weka.classifiers.Classifier;
import weka.classifiers.Evaluation;
import weka.classifiers.bayes.AODE;
import weka.classifiers.bayes.BayesNet;
import weka.classifiers.bayes.BayesianLogisticRegression;
import weka.classifiers.bayes.HNB;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.bayes.NaiveBayesMultinomial;
import weka.classifiers.bayes.NaiveBayesUpdateable;
import weka.classifiers.functions.SMO;
import weka.classifiers.meta.AdaBoostM1;
import weka.classifiers.meta.Bagging;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.DecisionStump;
import weka.classifiers.trees.J48;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ArffLoader;
import weka.core.converters.ArffSaver;
import weka.core.converters.CSVLoader;
import weka.core.tokenizers.NGramTokenizer;

import au.com.bytecode.opencsv.CSVReader;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;

import java.util.TimeZone;

public class sentiment {
	
public static void main(String[] args) throws Exception
	{            	
	    String theModel = "naiveBayesMulti"; //naiveBayesMulti, J48, naiveBayes 
		double correctThresh = 70.0;
		int theLimit = 2000;
	    double theSlice = 0.40;
	
		Properties prop = new Properties();
		prop.load(new FileInputStream("config.properties"));  
		
		String otherDb = prop.getProperty("otherDb");
		String otherCol = prop.getProperty("otherCol");
		String otherServer = prop.getProperty("otherServer");
		String otherPort = prop.getProperty("otherPort");

	    String body = "";
		int ratings[] = new int[4];
	    String[] theCols = null;
		
		FileWriter writerTrain = new FileWriter("/home/ubuntu/code/theData/train.arff");
		FileWriter writerTest = new FileWriter("/home/ubuntu/code/theData/test.arff");
		
		//NEUTRAL DATA//
		DBObject getSentimentNeut = new BasicDBObject();
		List<BasicDBObject> objNeut = new ArrayList<BasicDBObject>();
		objNeut.add(new BasicDBObject("rating", new BasicDBObject("$exists", true)));
		getSentimentNeut.put("$or", objNeut);	
	
		Mongo mNeut = new Mongo(otherServer, Integer.parseInt(otherPort));
		DB dbNeut = mNeut.getDB(otherDb);
		DBCollection collNeut = dbNeut.getCollection(otherCol);
		
		DBCursor cursorNeut = collNeut.find(getSentimentNeut).sort(new BasicDBObject("theDate",1)).limit(theLimit);
	//	System.out.println(cursorNeut.size());
		String mongoData = "";
		
		cursorNeut.hasNext();
		
		Map<Integer, String> neutData = new HashMap<Integer, String>();
		Map<Integer, String> bullData = new HashMap<Integer, String>();
		Map<Integer, String> bearData = new HashMap<Integer, String>();
	    int neutCnt = 0;
	    int bullCnt = 0;
	    int bearCnt = 0;
	    while (cursorNeut.hasNext()) 
	    {
	    	JSONParser parser = new JSONParser();
	    	
	    	mongoData = cursorNeut.next().toString();
	    	JSONObject json = (JSONObject)new JSONParser().parse(mongoData);		
			body = (String) json.get("body");
			
			Object objRating = parser.parse(json.get("rating").toString());
            JSONObject jsonObject = (JSONObject) objRating;
            
			Object ratTempBull = jsonObject.get("Bullish");
			Object ratTempNeut = jsonObject.get("Neutral");
			Object ratTempBear = jsonObject.get("Bearish");
			Object ratTempSpam = jsonObject.get("Spam");

			ratings[0] = Integer.parseInt(ratTempBull.toString());
			ratings[1] = Integer.parseInt(ratTempNeut.toString());
			ratings[2] = Integer.parseInt(ratTempBear.toString());
			ratings[3] = Integer.parseInt(ratTempSpam.toString());
			
			int maxIndex = 0;
			for (int z = 1; z < ratings.length; z++)
			{
			   int newnumber = ratings[z];
			   if ((newnumber > ratings[maxIndex]))
			   {
				   maxIndex = z;
			   }
			}
			//if(maxIndex == 1 || maxIndex == 3)
			{
				theCols = body.replaceAll("^\"", "").split("\"?(,|$)(?=(([^\"]*\"){2})*[^\"]*$) *\"?");
					
				theCols[0] = theCols[0].toLowerCase();
				theCols[0] = theCols[0].replaceAll("\\P{L}", " ");
				theCols[0] = theCols[0].replaceAll(" +", " ");
					
				String[] theOut = theCols[0].split(" ");
				List<String> myList = new ArrayList<String>();
				for(int k = 0; k < theOut.length; k++)
				{
					if(!(theOut[k] == null))
					{
						myList.add(theOut[k].replaceAll("[^\\w\\s]",""));
					}
				}
				theOut = myList.toArray(new String[0]);
				String temp = "";
				for(int k = 0; k < theOut.length; k++)
				{
					if (k == 0)
					{
						temp =  theOut[k];
					}
					if (k > 0)
					{
						temp = temp + " " + theOut[k]; 
					}
				} 
				if(temp != "" && (maxIndex == 1 || maxIndex == 3))
				{
					neutData.put(neutCnt,temp);
					neutCnt++;
				}
				if(temp != "" && maxIndex == 0)
				{
					bullData.put(bullCnt,temp);
					bullCnt++;
				}
				if(temp != "" && maxIndex == 2)
				{
					bearData.put(bearCnt,temp);
					bearCnt++;
				}
			}
	    }
	    cursorNeut.close();		
		System.out.println(neutData.size());

		int theMin = neutData.size();
	    int theTrain = (int) (theMin*theSlice);
	    
	    //TRAIN
	    writerTrain.append("@relation 'test'");
    	writerTrain.append('\n');
    	writerTrain.append('\n');
    	
	    writerTrain.append("@attribute text string");
    	writerTrain.append('\n');
    	
    	writerTrain.append("@attribute class-att {-1,0,1}");
    	writerTrain.append('\n');
    	writerTrain.append('\n');
    	
    	writerTrain.append("@data");
    	writerTrain.append('\n');

    	int neutSizeTrain = (int) (neutData.size()*theSlice);
    	int neutSizeTest = (int) (neutData.size()*(1-theSlice));
    	System.out.println(neutSizeTrain);
    	System.out.println(neutSizeTest);

    	for(int i=0;i<neutSizeTrain;i++)
	    {
	    	writerTrain.append("\""+neutData.get(i)+"\"" + "," + 0);	
			writerTrain.append('\n');
	    }

    	int bullSizeTrain = (int) (bullData.size()*theSlice);
    	int bullSizeTest = (int) (bullData.size()*(1-theSlice));
    	System.out.println(bullSizeTrain);
    	System.out.println(bullSizeTest);

    	for(int i=0;i<bullSizeTrain;i++)
	    {
	    	writerTrain.append("\""+bullData.get(i)+"\"" + "," + 1);	
			writerTrain.append('\n');
	    }

    	int bearSizeTrain = (int) (bearData.size()*theSlice);
    	int bearSizeTest = (int) (bearData.size()*(1-theSlice));
    	System.out.println(bearSizeTrain);
    	System.out.println(bearSizeTest);

    	for(int i=0;i<neutSizeTrain;i++)
	    {
	    	writerTrain.append("\""+bearData.get(i)+"\"" + "," + -1);	
			writerTrain.append('\n');
	    }
  	
		writerTrain.flush();
		writerTrain.close();
		
	    //TEST//
		writerTest.append("@relation 'test'");
		writerTest.append('\n');
		writerTest.append('\n');
	    	
		writerTest.append("@attribute text string");
		writerTest.append('\n');
	    	
		writerTest.append("@attribute class-att {-1,0,1}");
		writerTest.append('\n');
		writerTest.append('\n');
	    	
		writerTest.append("@data");
		writerTest.append('\n');

		for(int i=0;i<neutSizeTest ;i++)
		{
			writerTest.append("\""+neutData.get(i+neutSizeTrain)+"\"" + "," + 0);	
		    writerTest.append('\n');
		}

		for(int i=0;i<bullSizeTest ;i++)
		{
			writerTest.append("\""+bullData.get(i+bullSizeTrain)+"\"" + "," + 1);	
		    writerTest.append('\n');
		}

		for(int i=0;i<bearSizeTest ;i++)
		{
			writerTest.append("\""+bearData.get(i+bearSizeTrain)+"\"" + "," + -1);	
		    writerTest.append('\n');
		}

		writerTest.flush();
		writerTest.close();
		
		////////////////////////////
		///END PREPROCSSING STAGE///
		////////////////////////////

		String[] optionsClassifier = new String[16];
		optionsClassifier[0] = "-W";
		optionsClassifier[1] = "1000000";
		optionsClassifier[2] = "-prune-rate"; 
		optionsClassifier[3] = "-1.0"; 
		optionsClassifier[4] = "-C"; 
		optionsClassifier[5] = "-T";
		optionsClassifier[6] = "-I";
		optionsClassifier[7] = "-N";
		optionsClassifier[8] = "0";
		optionsClassifier[9] = "-S";  
		optionsClassifier[10] = "-stemmer";
		optionsClassifier[11] = "weka.core.stemmers.IteratedLovinsStemmer";	
		optionsClassifier[12] = "-M";
		optionsClassifier[13] = "1";
		optionsClassifier[14] = "-tokenizer";	
		optionsClassifier[15] = "weka.core.tokenizers.NGramTokenizer";
		
		BufferedReader reader = new BufferedReader(new FileReader("/home/ubuntu/code/theData/train.arff"));
		Instances train = new Instances(reader);
		reader.close();
		train.setClassIndex(train.numAttributes()-1);
		
		StringToWordVector filter = new StringToWordVector(); 
		filter = new StringToWordVector();
		filter.setAttributeIndices("first");
		filter.setOptions(optionsClassifier);
		FilteredClassifier classifier = new FilteredClassifier();
		classifier.setFilter(filter); new Remove();
		if (theModel.equals("naiveBayesMulti"))
		{
			classifier.setClassifier(new NaiveBayesMultinomial());
		}
		if (theModel.equals("J48"))
		{
			classifier.setClassifier(new J48());
		}
		if (theModel.equals("naiveBayes"))
		{
			classifier.setClassifier(new NaiveBayes());
		}
		
		classifier.buildClassifier(train);
		
		BufferedReader readerTest = new BufferedReader(new FileReader("/home/ubuntu/code/theData/test.arff"));
		Instances test = new Instances(readerTest);
		readerTest.close();
		test.setClassIndex(test.numAttributes()-1);

		Evaluation eval = new Evaluation(test);
		eval.crossValidateModel(classifier, test, 10, new Random(1));
		Double pctCorr = eval.pctCorrect();
		System.out.println(eval.toSummaryString());
		System.out.println(eval.toMatrixString());
		
		//WRITE MODEL OUT FOR USE WITH streamTwtr.java
		if(pctCorr >= correctThresh)
		{
			System.out.println("SAVING MODEL, ACCURACY IS: " + pctCorr);
			ObjectOutputStream myModelWrite = new ObjectOutputStream(
	        new FileOutputStream("/home/ubuntu/code/theData/" + theModel + ".model"));
			myModelWrite.writeObject(classifier);
			myModelWrite.flush();
			myModelWrite.close();
		}
		else
		{
			System.out.println("***WARNING*** NOT SAVING MODEL, ACCURACY IS BELOW OUR THRESHOLD: " + pctCorr);
		}
	} 
}
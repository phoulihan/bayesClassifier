import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Random;

import org.json.simple.JSONObject;
import org.json.simple.parser.JSONParser;

import com.mongodb.BasicDBObject;
import com.mongodb.DB;
import com.mongodb.DBCollection;
import com.mongodb.DBCursor;
import com.mongodb.DBObject;
import com.mongodb.Mongo;

import weka.classifiers.Evaluation;
import weka.classifiers.bayes.NaiveBayes;
import weka.classifiers.bayes.NaiveBayesMultinomial;
import weka.classifiers.meta.FilteredClassifier;
import weka.classifiers.trees.J48;
import weka.core.Instances;
import weka.filters.unsupervised.attribute.Remove;
import weka.filters.unsupervised.attribute.StringToWordVector;

import edu.stanford.nlp.tagger.maxent.MaxentTagger;

import java.util.TimeZone;

public class sentiment {
	
public static void main(String[] args) throws Exception
	{            	
	    String theModel = "naiveBayesMulti"; //naiveBayesMulti, J48, naiveBayes 
		double correctThresh = 70.0;
		int theLimit = 1000;
	    double theSlice = 0.90;
	
		Properties prop = new Properties();
		prop.load(new FileInputStream("config.properties"));  
		
		String theDb = prop.getProperty("theDb");
		String theCol = prop.getProperty("theCol");
		String amznServer = prop.getProperty("theServer");
		String port = prop.getProperty("port");
		String otherDb = prop.getProperty("otherDb");
		String otherCol = prop.getProperty("otherCol");
		String otherServer = prop.getProperty("otherServer");
		String otherPort = prop.getProperty("otherPort");

	    String body = "";
		String Neutral = "";
		String Bearish = "";
		String Bullish = "";
		String Spam = "";
		int ratings[] = new int[4];
	    String[] theCols = null;
		
		FileWriter writerTrain = new FileWriter("myPATH/train.arff");
		FileWriter writerTest = new FileWriter("myPATH/test.arff");
		
		//NEUTRAL DATA//
		DBObject getSentimentNeut = new BasicDBObject();
		List<BasicDBObject> objNeut = new ArrayList<BasicDBObject>();
		objNeut.add(new BasicDBObject("rating", new BasicDBObject("$exists", true)));
		getSentimentNeut.put("$or", objNeut);	
	
		Mongo mNeut = new Mongo(otherServer, Integer.parseInt(otherPort));
		DB dbNeut = mNeut.getDB(otherDb);
		DBCollection collNeut = dbNeut.getCollection(otherCol);
		
		DBCursor cursorNeut = collNeut.find(getSentimentNeut).sort(new BasicDBObject("theDate",1)).limit(theLimit);
		//System.out.println(cursorNeut.size());
		String mongoData = "";
		
		cursorNeut.hasNext();
		
		Map<Integer, String> neutData = new HashMap<Integer, String>();
	    int i = 0;
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
			for (int z = 0; z < ratings.length; z++)
			{
			   int newnumber = ratings[z];
			   if ((newnumber > ratings[maxIndex]))
			   {
				   maxIndex = z;
			   }
			}
			if(maxIndex == 1 || maxIndex == 3)
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
				if(temp != "")
				{
					neutData.put(i,temp);
					i++;
				}
			}
	    }
	    cursorNeut.close();		
		System.out.println(neutData.size());
		
		///BEAR DATA///
		DBObject getSentimentBear = new BasicDBObject();
		List<BasicDBObject> objBear = new ArrayList<BasicDBObject>();
		objBear.add(new BasicDBObject("sentiment", "Bearish"));
		getSentimentBear.put("$or", objBear);	
	
		Mongo m = new Mongo(amznServer, Integer.parseInt(port));
		DB dbBear = m.getDB(theDb);
		DBCollection collBear = dbBear.getCollection(theCol);
		
		DBCursor cursorBear = collBear.find(getSentimentBear).sort(new BasicDBObject("theDate",1)).limit(neutData.size()*2);
		//System.out.println(cursorBear.size());
		mongoData = "";
		
		cursorBear.hasNext();
		
		Map<Integer, String> bearData = new HashMap<Integer, String>();
	    i = 0;
	    while (cursorBear.hasNext()) 
	    {
	    	mongoData = cursorBear.next().toString();
	    	JSONObject json = (JSONObject)new JSONParser().parse(mongoData);		
			body = (String) json.get("body");
				
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
			if(temp != "")
			{
				bearData.put(i,temp);
				i++;
			}
	    }
	    cursorBear.close();
	    
	   // bearData.put(i,"apple fails to beat analyst estimates");
		
		///BULL DATA///
		DBObject getSentimentBull = new BasicDBObject();
		List<BasicDBObject> objBull = new ArrayList<BasicDBObject>();
		objBull.add(new BasicDBObject("sentiment", "Bullish"));
		getSentimentBull.put("$or", objBull);	
		
		Mongo mBull = new Mongo(amznServer , Integer.parseInt(port));
		DB dbBull = mBull.getDB(theDb);
		DBCollection collBull = dbBull.getCollection(theCol);
		
		DBCursor cursorBull = collBull.find(getSentimentBull).sort(new BasicDBObject("theDate",1)).limit(neutData.size()*2);
		//System.out.println(2*bearData.size());
		
		Map<Integer, String> bullData = new HashMap<Integer, String>();
	    i = 0;
	    while (cursorBull.hasNext()) 
	    {
	    		mongoData = cursorBull.next().toString();
	    		JSONObject json = (JSONObject)new JSONParser().parse(mongoData);		
				body = (String) json.get("body"); 
				
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
				
				if(theOut.length > 0)
				{
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
					if(temp != "")
					{
						bullData.put(i,temp);
						i++;
					}
				}
	    }
	    cursorBull.close();
	    // System.out.println(cursorBear.size());
	    
	    //int theMin = Math.min(bearData.size(),bullData.size());
	    //theMin = Math.min(theMin,neutData.size());
	    
	  //  int theTrain = (int) (2*theMin*theSlice);
	  //  int theTest = 2*theMin - theTrain;
		
	    int theTrain = (int) (3*(neutData.size())*theSlice);
		int theTest = (int) (3*(neutData.size()) - theTrain);
	    
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
	    for(i=0;i<neutSizeTrain;i++)
	    {
			writerTrain.append("\""+bearData.get(i)+"\"" + "," + -1);	
			writerTrain.append('\n');
	    	writerTrain.append("\""+neutData.get(i)+"\"" + "," + 0);	
			writerTrain.append('\n');	
	    	writerTrain.append("\""+bullData.get(i)+"\"" + "," + 1);
			writerTrain.append('\n');
			//System.out.println(neutData.get(i));
	    }
	    
	    for(i=0;i<(cursorBear.size() - neutSizeTrain)/2;i++)
	    {
	    	//writerTrain.append("\""+neutData.get(i)+"\"" + "," + 0);	
			//writerTrain.append('\n');
	    	writerTrain.append("\""+bullData.get(i)+"\"" + "," + 1);	
			writerTrain.append('\n');
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

		for(i=0;i<neutSizeTest ;i++)
		{
		    writerTest.append("\""+bearData.get(i+neutSizeTrain)+"\"" + "," + -1);	
		    writerTest.append('\n');
			writerTest.append("\""+neutData.get(i+neutSizeTrain)+"\"" + "," + 0);	
		    writerTest.append('\n');
			writerTest.append("\""+bullData.get(i+neutSizeTrain)+"\"" + "," + 1);
		    writerTest.append('\n');
		   // System.out.println(neutSizeTrain);
		   // System.out.println(neutData.size());
		}
		
		for(i=0;i<(cursorBear.size()-neutSizeTest)/2;i++)
		{
			writerTest.append("\""+bullData.get(i+theTrain/2)+"\"" + "," + 1);	
		    writerTest.append('\n');
		    writerTest.append("\""+bearData.get(i+theTrain/2)+"\"" + "," + -1);	
		    writerTest.append('\n');
		}
		
		writerTest.flush();
		writerTest.close();
	
		bullData = null;
		bearData = null;
		
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
	//	optionsClassifier[16] = "-min";
	//	optionsClassifier[17] = "5";
	//	optionsClassifier[18] = "-max";
	//	optionsClassifier[19] = "5";
		
		BufferedReader reader = new BufferedReader(new FileReader("myPATH/train.arff"));
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
		
		BufferedReader readerTest = new BufferedReader(new FileReader("myPATH/test.arff"));
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
	        new FileOutputStream("myPATH/" + theModel + ".model"));
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

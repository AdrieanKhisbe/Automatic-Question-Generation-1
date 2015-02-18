// Question Generation via Overgenerating Transformations and Ranking
// Copyright (c) 2008, 2009 Carnegie Mellon University.  All Rights Reserved.
//
// This program is free software; you can redistribute it and/or
// modify it under the terms of the GNU General Public License
// as published by the Free Software Foundation; either version 2
// of the License, or (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program; if not, write to the Free Software
// Foundation, Inc., 59 Temple Place - Suite 330, Boston, MA  02111-1307, USA.
//
// For more information, bug reports, fixes, contact:
//    Michael Heilman
//	  Carnegie Mellon University
//	  mheilman@cmu.edu
//	  http://www.cs.cmu.edu/~mheilman



package edu.cmu.ark;


// Adding OpenAryhype to the build path of this project also works!

///import info.ephyra.nlp.StanfordParser;
//import info.ephyra.nlp.TreeUtil;




import java.io.*;
//import java.text.NumberFormat;
import java.util.*;

import distractorgeneration.DistractorGenerator;
import edu.stanford.nlp.trees.CollinsHeadFinder;
//import edu.cmu.ark.ranking.WekaLinearRegressionRanker;
import edu.stanford.nlp.trees.Tree;
import edu.stanford.nlp.trees.tregex.ParseException;
import edu.stanford.nlp.trees.tregex.TregexMatcher;
import edu.stanford.nlp.trees.tregex.TregexPattern;


/**
 * Wrapper class for outputting a (ranked) list of questions given an entire document,
 * not just a sentence.  It wraps the three stages discussed in the technical report and calls each in turn 
 * (along with parsing and other preprocessing) to produce questions.
 * 
 * This is the typical class to use for running the system via the command line. 
 * 
 * Example usage:
 * 
    java -server -Xmx800m -cp lib/weka-3-6.jar:lib/stanford-parser-2008-10-26.jar:bin:lib/jwnl.jar:lib/commons-logging.jar:lib/commons-lang-2.4.jar:lib/supersense-tagger.jar:lib/stanford-ner-2008-05-07.jar:lib/arkref.jar \
	edu/cmu/ark/QuestionAsker \
	--verbose --simplify --group \
	--model models/linear-regression-ranker-06-24-2010.ser.gz \
	--prefer-wh --max-length 30 --downweight-pro
 * 
 * @author mheilman@cs.cmu.edu
 *
 */
public class QuestionAsker {

	public static HashSet<String>nounPhraseSet = new HashSet<String>();
	public static boolean isNounPhraseSetPopulated = false;
	
	public QuestionAsker(){
		try {
			StanfordParser.initialize();
		} catch (Exception e) {
			System.out.println("Stanford Parser-Error:"+e.toString());
			//MsgPrinter.printErrorMsg("Could not create Stanford parser."+e.toString());
		}

	}
	
	
	
	
	/**
	 * @param args
	 * @throws ParseException 
	 */
	
	public static void main(String[] args) throws ParseException {
		
		QuestionTransducer qt = new QuestionTransducer();
		InitialTransformationStep trans = new InitialTransformationStep();
		QuestionRanker qr = null;
		
		
		qt.setAvoidPronounsAndDemonstratives(false);
		
		//pre-load
		AnalysisUtilities.getInstance();
		
		String buf;
		Tree parsed;
		boolean printVerbose = true;//setting printVerbose true always
		String modelPath = null;
		
		List<Question> outputQuestionList = new ArrayList<Question>();
		boolean preferWH = false;
		boolean doNonPronounNPC = false;
		boolean doPronounNPC = true;
		Integer maxLength = 1000;
		boolean downweightPronouns = false;
		boolean avoidFreqWords = false;
		boolean dropPro = true;
		boolean justWH = false;
		
		for(int i=0;i<args.length;i++){
			if(args[i].equals("--debug")){
				GlobalProperties.setDebug(true);
			}else if(args[i].equals("--verbose")){
				printVerbose = true;
			}else if(args[i].equals("--model")){ //ranking model path
				modelPath = args[i+1]; 
				i++;
			}else if(args[i].equals("--keep-pro")){
				dropPro = false;
			}else if(args[i].equals("--downweight-pro")){
				dropPro = false;
				downweightPronouns = true;
			}else if(args[i].equals("--downweight-frequent-answers")){
				avoidFreqWords = true;
			}else if(args[i].equals("--properties")){  
				GlobalProperties.loadProperties(args[i+1]);
			}else if(args[i].equals("--prefer-wh")){  
				preferWH = true;
			}else if(args[i].equals("--just-wh")){  
				justWH = true;
			}else if(args[i].equals("--full-npc")){  
				doNonPronounNPC = true;
			}else if(args[i].equals("--no-npc")){  
				doPronounNPC = false;
			}else if(args[i].equals("--max-length")){  
				maxLength = new Integer(args[i+1]);
				i++;
			}
		}
		
		qt.setAvoidPronounsAndDemonstratives(dropPro);
		trans.setDoPronounNPC(doPronounNPC);
		trans.setDoNonPronounNPC(doNonPronounNPC);
		
		if(modelPath != null){
			System.err.println("Loading question ranking models from "+modelPath+"...");
			qr = new QuestionRanker();
			qr.loadModel(modelPath);
		}
		
		try{
			while(true){
				if(GlobalProperties.getDebug()) System.err.println("\nInput TextFile:");
				String doc;
	
				String query = readLine().trim();
				System.out.println("Query read is :"+query);
				//Right now input takes only File TODO: Do it for other types of inputstream also
				if (query.startsWith("FILE: ")||query.startsWith("file: ")) {
						System.out.println("Yes it is file");
						// when input is the following format:
						// FILE: in.txt out.txt
						// read text from in.txt and output to out.txt
						String fileLine = query.substring(6).trim();
						String[] files = fileLine.split("\\s+");
						if (files.length != 2) {
							System.out.println("FILE field must only contain two valid files. e.g.:");
							System.out.println("FILE: input.txt output.txt");
							continue;
						}
			//	processTextFile(files[0],files[1]);
						FileReader in = new FileReader(files[0]);
					    BufferedReader br = new BufferedReader(in);	
				
			
			while(true){
				outputQuestionList.clear();
				doc = "";
				buf = "";
				
				buf = br.readLine();
				if(buf == null){
					break;
				}
				doc += buf;
				
				while(br.ready()){
					buf = br.readLine();
					if(buf == null){
						break;
					}
					if(buf.matches("^.*\\S.*$")){
						doc += buf + " ";
					}else{
						doc += "\n";
					}
				}
				if(doc.length() == 0){
					break;
				}
				
				long startTime = System.currentTimeMillis();
				List<String> sentences = AnalysisUtilities.getSentences(doc);
				
				//iterate over each segmented sentence and generate questions
				List<Tree> inputTrees = new ArrayList<Tree>();
				
				for(String sentence: sentences){
					if(GlobalProperties.getDebug()) System.err.println("Question Asker: sentence: "+sentence);
					
					parsed = AnalysisUtilities.getInstance().parseSentence(sentence).parse;
					inputTrees.add(parsed);
				}
				
				if(GlobalProperties.getDebug()) System.err.println("Seconds Elapsed Parsing:\t"+((System.currentTimeMillis()-startTime)/1000.0));
				
				//step 1 transformations
				List<Question> transformationOutput = trans.transform(inputTrees);
				
				//step 2 question transducer
				for(Question t: transformationOutput){
					if(GlobalProperties.getDebug()) System.err.println("Stage 2 Input: "+t.getIntermediateTree().yield().toString());
					qt.generateQuestionsFromParse(t);
					outputQuestionList.addAll(qt.getQuestions());
				}			
				
				//remove duplicates
				QuestionTransducer.removeDuplicateQuestions(outputQuestionList);
				
				//step 3 ranking
				if(qr != null){
					qr.scoreGivenQuestions(outputQuestionList);
					boolean doStemming = true;
					QuestionRanker.adjustScores(outputQuestionList, inputTrees, avoidFreqWords, preferWH, downweightPronouns, doStemming);
					QuestionRanker.sortQuestions(outputQuestionList, false);
				}
				
				//now print the questions
				
				String ansPhrase="";
				for(Question question: outputQuestionList){
					if(question.getTree().getLeaves().size() > maxLength){
						continue;
					}
					if(justWH && question.getFeatureValue("whQuestion") != 1.0){
						continue;
					}
					System.out.println("Question :"+question.yield());
					//if(printVerbose) System.out.print("\t"+AnalysisUtilities.getCleanedUpYield(question.getSourceTree()));
					Tree ansTree = question.getAnswerPhraseTree();
					
					//if(printVerbose) System.out.print("\t");
					if(ansTree != null){
						ansPhrase = AnalysisUtilities.getCleanedUpYield(question.getAnswerPhraseTree());
						System.out.println("Answer Phrase detected :"+ansPhrase);
						if (!isAnsPhraseProperNoun(ansPhrase)&&(countWords(ansPhrase)>=2)) {
							System.out.println("Resolving answerphrase to a single word...");
							String headWord=resolveHead(ansPhrase);
							if(headWord!=null){
								ansPhrase=headWord;
							}
						}
					}
					System.out.println("Answer Phrase :"+ansPhrase);
					//if(printVerbose) 
					System.out.println("Score :"+question.getScore());
					String ansSentence = question.getSourceTree().yield().toString();
					System.out.println("Answer Sentence :"+ansSentence);
					
					//System.err.println("Answer depth: "+question.getFeatureValue("answerDepth"));
				//our code for distractor generator
					int distractorCount=0;
					//System.out.println(" Question :"+question.yield());
				    //System.out.println(" Answer phrase :"+ansPhrase);
					System.out.println("Distractor generation starts:");
					//NOTE: call POS Tagger before SST because POS Tagger will group all adjacent proper noun together
					//which is then used by SST tagger
					List<String>posList=DistractorGenerator.getPOSTaggerDistractors(files[0], ansPhrase);
					
					List<String>sstList=DistractorGenerator.getSSTTaggerDistractors(files[0], ansPhrase);
					System.out.println("Ranking POS distractors");
					DistractorGenerator.rankDistractor(ansSentence, ansPhrase, posList);
					System.out.println("No. of SST Distractors found :"+(sstList.size()-1));
					//stage 1 SuperSenseTagger
											   
					if(sstList.size()>=2){
						distractorCount+=(sstList.size()-1);
					    for(int i=1;i<sstList.size();i++){
				//	    	System.out.println("Distractor :"+sstList.get(i));
					    }
					}
					//Distractor ranking
					//substitute the answer phrase with the distractor and check the probability of N-gram using
					//microsoft bing api 
					
					
					//stage 2 POSTagger
						System.out.println("No. of POS Distractors found :"+(posList.size()-1));
						distractorCount+=(posList.size()-1);
					    for(int i=1;i<posList.size();i++){
					//    	System.out.println("Distractor :"+posList.get(i));
					    }
					
		

			}
			}//child while block ends
			}
		}//parent while block ends
		//		System.out.println("QA with distractor generation done :-) ");
		
			

//					System.out.println();
				
			
		//		if(GlobalProperties.getDebug()) System.err.println("Seconds Elapsed Total:\t"+((System.currentTimeMillis()-startTime)/1000.0));
				//prompt for another piece of input text 
	//			if(GlobalProperties.getDebug()) System.err.println("\nInput Text:");
		}//try block ends
		catch(Exception e){
			e.printStackTrace();
		}
	}

	public static void printFeatureNames(){
		List<String> featureNames = Question.getFeatureNames();
		for(int i=0;i<featureNames.size();i++){
			if(i>0){
				System.out.print("\n");
			}
			System.out.print(featureNames.get(i));
		}
		System.out.println();
	}
	
	public static String resolveHead(String ansPhrase) throws ParseException{
		int count=0;
		String tregexMatchNounModifier = "NP=noun";
		TregexPattern tregexPatternMatchNounModifier;
		TregexMatcher tregexMatcher;
		tregexPatternMatchNounModifier = TregexPattern.compile(tregexMatchNounModifier);
		CollinsHeadFinder headFinder = new CollinsHeadFinder();
		try {
			StanfordParser.initialize();
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Tree tree = StanfordParser.parseTree(ansPhrase);
		tregexMatcher = tregexPatternMatchNounModifier.matcher(tree);
		while (tregexMatcher.find()) {
			
			Tree nounTree = tregexMatcher.getNode("noun");
			Tree npHeadTree = headFinder.determineHead(nounTree);
       		String headTag = TreeUtil.getLabel(npHeadTree);
		    //System.out.println(npHeadTree.toString());
			count=countWords(headTag);
			if(count==1)
			{
				//System.out.println("[Answer]: "+headTag+"\n\n");
				
				return headTag;
			}
			
		
		}
		return null;
	}
	
	public static int countWords(String s){

	    int wordCount = 0;

	    boolean word = false;
	    int endOfLine = s.length() - 1;

	    for (int i = 0; i < s.length(); i++) {
	
	        if (Character.isLetter(s.charAt(i)) && i != endOfLine) {
	            word = true;
	            
	        } else if (!Character.isLetter(s.charAt(i)) && word) {
	            wordCount++;
	            word = false;
	          
	        } else if (Character.isLetter(s.charAt(i)) && i == endOfLine) {
	            wordCount++;
	        }
	    }
	    return wordCount;
	}
	//after POS tag merges all proper nouns together, it writes the proper noun list as file
	//read the file and populate the nounPhrase Set
	 public static boolean populateNounPhraseSet(){
	    	if(isNounPhraseSetPopulated==false){
	    		System.out.println("populating nounPhrase Set");
	    		BufferedReader in = null;
	    		try {
	    			in = new BufferedReader(new FileReader(new File("/home/vishnu/fyp_resources/temp/nounphrases.txt")));
	    		} catch (FileNotFoundException e) {
	    			// TODO Auto-generated catch block
	    			e.printStackTrace();
	    		}
	    		String nounPhraseFileText="";
	    		try {
	    			while (in.ready()) {
	    				nounPhraseFileText+= in.readLine().trim();
	    			}

	    	 		in.close();
	    		} catch (IOException e) {
	    			// TODO Auto-generated catch block
	    			e.printStackTrace();
	    		}

	    		System.out.println("Text read from nounPhraseFile :"+nounPhraseFileText);
	    		String[] nounPhraseList=nounPhraseFileText.split("\\|");
	    		System.out.println("Reading nounphrases");  
	    		for(String nounPhraseWithStar:nounPhraseList){
	    			String[] nounWords=nounPhraseWithStar.split("\\*");
	    			boolean firstTime = true;
	    		    String nounPhrase = "";
	    		    for (String word : nounWords) {
	    		        if (firstTime) {
	    		            firstTime = false;
	    		        } else {
	    		            nounPhrase+=" ";
	    		        }
	    		        nounPhrase+=word;
	    		    }
	    		    nounPhraseSet.add(nounPhrase);
	    		    
	    		}
	    		  isNounPhraseSetPopulated=true; 
	    	}
	    	
	    	return true;
	    }
		public static boolean isAnsPhraseProperNoun(String ansPhrase) {
			// TODO Auto-generated method stub
			if(nounPhraseSet.contains(ansPhrase)){
				return true;
			}
			return false;
		}
		protected static String readLine() {
			try {
				return new java.io.BufferedReader(new
					java.io.InputStreamReader(System.in)).readLine();
			}
			catch(java.io.IOException e) {
				return new String("");
			}
		}


	
}
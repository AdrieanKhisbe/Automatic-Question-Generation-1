package edu.cmu.ark;

import edu.stanford.nlp.trees.tregex.ParseException;

public class Tester {
	 public static void main(String[] args) throws ParseException{

		 QuestionAsker qa = new QuestionAsker();
		 qa.resolveHead("old lady's bag");
	 
	 }
}
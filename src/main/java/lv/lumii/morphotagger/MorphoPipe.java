/*******************************************************************************
 * Copyright 2012,2013,2014 Institute of Mathematics and Computer Science, University of Latvia
 * Author: Pēteris Paikens
 * 
 *     This program is free software: you can redistribute it and/or modify
 *     it under the terms of the GNU General Public License as published by
 *     the Free Software Foundation, either version 3 of the License, or
 *     (at your option) any later version.
 * 
 *     This program is distributed in the hope that it will be useful,
 *     but WITHOUT ANY WARRANTY; without even the implied warranty of
 *     MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 *     GNU General Public License for more details.
 * 
 *     You should have received a copy of the GNU General Public License
 *     along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *******************************************************************************/
package lv.lumii.morphotagger;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.io.PrintWriter;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map.Entry;

import lv.semti.morphology.analyzer.Splitting;
import lv.semti.morphology.analyzer.Word;
import lv.semti.morphology.analyzer.Wordform;
import lv.semti.morphology.attributes.AttributeNames;
import lv.semti.morphology.attributes.AttributeValues;

import org.json.simple.JSONValue;

import edu.stanford.nlp.ie.ner.CMMClassifier;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ExtraColumnAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVMorphologyAnalysis;
import edu.stanford.nlp.ling.CoreAnnotations.ParentAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.TextAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.ling.Datum;
import edu.stanford.nlp.sequences.LVMorphologyReaderAndWriter;

// Copied/pasted/mangled from transliteration webservices java project

public class MorphoPipe {
	private enum inputTypes {SENTENCE, PARAGRAPH, VERT, CONLL, JSON};
	private enum outputTypes {JSON, TAB, VERT, MOSES, CONLL_X, XML, VISL_CG, lemmatizedText, lowercasedText, analyzerOptions};

	private static String eol = System.getProperty("line.separator");
	private static String field_separator = "\t";
	private static String token_separator = eol;
	
	private static boolean mini_tag = false;		
	private static boolean features = false;	
	private static boolean LETAfeatures = false;
	private static inputTypes inputType = inputTypes.PARAGRAPH;
	private static outputTypes outputType = outputTypes.CONLL_X;
	//private static int sentencelengthcap = Splitting.DEFAULT_SENTENCE_LENGTH_CAP;
	private static int sentencelengthcap = 250;
	private static boolean saveColumns = false;
	private static boolean keepTags = false;
	private static boolean saveCase = false; // for lemmatized text output format
	private static boolean outputSeparators = false; // <s> for sentences, <p> for paragraphs
    private static boolean whitespaceMarker = false;
	private static boolean stopOnEmpty = true; // quit on empty line
	
	private static String morphoClassifierLocation = "models/lv-morpho-model.ser.gz"; //FIXME - make it configurable
	
	public static void main(String[] args) throws Exception {
		
		for (int i=0; i<args.length; i++) {
			if (args[i].equalsIgnoreCase("-tab")) {  // one response line per each query line, tab-separated
				outputType = outputTypes.TAB;
				token_separator = "\t";
			}
			if (args[i].equalsIgnoreCase("-vert")) { // one response line per token, tab-separated
				outputType = outputTypes.VERT;
			}
			if (args[i].equalsIgnoreCase("-moses")) { // one response line per token, pipe-separated
				field_separator = "|";
				token_separator = " ";
				outputType = outputTypes.MOSES;
			}
			if (args[i].equalsIgnoreCase("-stripped")) mini_tag = true; //remove nonlexical attributes
			if (args[i].equalsIgnoreCase("-features")) features = true; //output training features
			if (args[i].equalsIgnoreCase("-leta")) LETAfeatures = true; //output specific features for LETA semantic frame analysis
			if (args[i].equalsIgnoreCase("-vertinput")) inputType = inputTypes.VERT; //vertical input format as requested by Milos Jakubicek 2012.11.01
			if (args[i].equalsIgnoreCase("-paragraphs")) {
				inputType = inputTypes.PARAGRAPH;
				if (i+1 < args.length && !args[i+1].startsWith("-")) {
					try {
						sentencelengthcap = Integer.parseInt(args[i+1]);
						System.err.printf("Sentence length capped to %d\n", sentencelengthcap);
						i++;
					} catch (Exception e) {
						System.err.printf("Error when parsing command line param '%s %s'\n",args[i], args[i+1]);
						System.err.println(e.getMessage());
					}
				}
			}
			if (args[i].equalsIgnoreCase("-conll-in")) inputType = inputTypes.CONLL; 
			if (args[i].equalsIgnoreCase("-json-in")) inputType = inputTypes.JSON; 
			if (args[i].equalsIgnoreCase("-conll-x")) outputType = outputTypes.CONLL_X;
			if (args[i].equalsIgnoreCase("-xml")) outputType = outputTypes.XML;
			if (args[i].equalsIgnoreCase("-visl-cg")) outputType = outputTypes.VISL_CG;
			if (args[i].equalsIgnoreCase("-lemmatized-text")) outputType = outputTypes.lemmatizedText;
            if (args[i].equalsIgnoreCase("-lowercased-text")) outputType = outputTypes.lowercasedText;
            if (args[i].equalsIgnoreCase("-analyzer")) {
                outputType = outputTypes.analyzerOptions;
                token_separator = "\t";
            }
			if (args[i].equalsIgnoreCase("-saveColumns")) saveColumns = true; //save extra columns from conll input
			if (args[i].equalsIgnoreCase("-unix-line-endings")) eol="\n";
			if (args[i].equalsIgnoreCase("-keep-tags")) keepTags = true;
			if (args[i].equalsIgnoreCase("-output-separators")) outputSeparators = true;
            if (args[i].equalsIgnoreCase("-whitespace-marker")) whitespaceMarker = true;
            if (args[i].equalsIgnoreCase("-allow-empty-lines")) stopOnEmpty = false;
						
			if (args[i].equalsIgnoreCase("-h") || args[i].equalsIgnoreCase("--help") || args[i].equalsIgnoreCase("-?")) {
				System.out.println("LV morphological tagger");
				System.out.println("\nInput formats");
				System.out.println("\tDefault : plain text UTF-8, one sentence per line, terminated by a blank line.");
				System.out.println("\t-paragraphs [lengthcap]: plain text UTF-8, each line will be split in sentences. In output, paragraph borders are noted by an extra blank line. If lengthcap parameter is provided, then sentence length will be limited to that, instead of the default of " + sentencelengthcap);
				System.out.println("\t-vertinput : one line per token, sentences separated by <s></s>. Any XML-style tags are echoed as-is. \n\t\tNB! sentences are retokenized, the number of tokens may be different.");
				System.out.println("\t-conll-in : CONLL shared task data format - one line per token, with tab-delimited columns, sentences separated by blank lines.");
				System.out.println("\t-json-in : one line per sentence, each line contains a single json array of strings-tokens.");
				System.out.println("\nOutput formats");
				System.out.println("\tDefault : JSON. Each sentence is returned as a list of dicts, each dict contains elements 'Word', 'Tag' and 'Lemma'.");
				System.out.println("\t-tab : one response line for each query line; tab-separated lists of word, tag and lemma.");
				System.out.println("\t-vert : one response line for each token; tab-separated lists of word, tag and lemma.");
				System.out.println("\t-moses : one response line for each token; pipe-separated lists of word, tag and lemma.");
				System.out.println("\t-conll-x : CONLL-X shared task data format - one line per token, with tab-delimited columns, sentences separated by blank lines.");
				System.out.println("\t-xml : one xml word per line");
				System.out.println("\t-visl-cg : output format for VISL constraint grammar tool");
				System.out.println("\t-lemmatized-text : output lowercase lemmatized text, each sentence in new row, tokens seperated by single space");
				System.out.println("\t-lowercased-text : output lowercased text, each sentence in new row, tokens seperated by single space");
                System.out.println("\t-analyzer : one response line for each token; word followed by a tab-separated list of undisambiguated morphological tag options");
				System.out.println("\nOther options:");
				System.out.println("\t-stripped : lexical/nonessential parts of the tag are replaced with '-' to reduce sparsity.");
				System.out.println("\t-features : in conll output, include the features that were used for training/tagging.");
				System.out.println("\t-leta : in conll output, include extra features used for semantic frame analysis.");
				System.out.println("\t-saveColumns : save extra columns from conll input.");
				System.out.println("\t-unix-line-endings : use \\n line endings for output even on windows systems");
				System.out.println("\t-keep-tags : preserve lines that start with '<' to enable xml-style metadata");
				System.out.println("\t-output-separators : put <s></s> sentence markup and <p></p> paragraph markup");
				System.out.println("\t-whitespace-marker : put <g /> tags where the tokens did not have whitespace between them");
                System.out.println("\t-allow-empty-lines : do not quit on blank lines input (as per default)");
				System.out.flush();
				System.exit(0);
			}
		}
						
		CMMClassifier<CoreLabel> morphoClassifier = CMMClassifier.getClassifier(morphoClassifierLocation);
			
		PrintStream out = new PrintStream(System.out, true, "UTF8");
		BufferedReader in = new BufferedReader(new InputStreamReader(System.in, "UTF8"));
		
		switch(inputType) {
		case CONLL:
			for (List<CoreLabel> sentence : readCONLL(in)) {
		    	outputSentence(morphoClassifier, out, sentence);
			}
			break;
		default:
		    String s;
		    String sentence = "";
		    while ((s = in.readLine()) != null && (s.length() != 0 || !stopOnEmpty)) {
		    	if (s.startsWith("<") && s.length()>1 && keepTags) {
		    		if (outputType != outputTypes.lemmatizedText && outputType != outputTypes.lowercasedText) out.println(s);
		    		continue;
		    	}
                if (s.length() == 0) continue;
		    	boolean finished = true; // is sentence finished and ready to analyze
		    	if (inputType != inputTypes.VERT) {		    		
		    		sentence = s;
		    	} else {
		    		if (s.startsWith("<") && s.length()>1) out.println(s);
		    		else sentence = sentence + " " + s;
		    		finished = s.startsWith("</s>");
		    	}	    	
		    	if (finished) {
		    		processSentences(morphoClassifier, out, sentence.trim());
			    	sentence = "";
		    	}
		    }
	    	if (inputType != inputTypes.VERT && sentence.length()>0) { //FIXME, not DRY
	    		processSentences(morphoClassifier, out, sentence.trim());
	    	}	    			
		}
		in.close();
		out.close();
	}

	/**
	 * Splits the text in sentences if needed, and forwards to outputSentance
	 * @param cmm - the tagger, needed to retrieve tagger features if they are requested
	 * @param out - a stream to output the data
	 * @param text - actual tokens to be output
	 */
	public static void processSentences(
			CMMClassifier<CoreLabel> cmm, PrintStream out, String text) {
		
		if (inputType == inputTypes.PARAGRAPH) { // split in multiple sentences
			if (outputSeparators) out.println("<p>");
			LinkedList<LinkedList<Word>> sentences = Splitting.tokenizeSentences(LVMorphologyReaderAndWriter.getAnalyzer(), text, sentencelengthcap);
			for (LinkedList<Word> sentence : sentences) 
				outputSentence(cmm, out, LVMorphologyReaderAndWriter.analyzeSentence2(sentence) );
			if (outputSeparators) 
				out.println("</p>");
			else 
				out.println();
		} else outputSentence(cmm, out, LVMorphologyReaderAndWriter.analyzeSentence(text) ); // just a single sentence for other types
	}

	/**
	 * Outputs the tagged sentence according to the outputType set in this class
	 * @param cmm - the tagger, needed to retrieve tagger features if they are requested
	 * @param out - a stream to output the data
	 * @param sentence - actual tokens to be output
	 */
	public static void outputSentence(CMMClassifier<CoreLabel> cmm,
			PrintStream out, List<CoreLabel> sentence) {
		if (outputSeparators) out.println("<s>");

        if (outputType != outputTypes.lowercasedText && outputType != outputTypes.analyzerOptions) { //FIXME - a separate flag would be better
            sentence = cmm.classify(sentence); // runs the actual morphotagging system
        }

		switch (outputType) {
		case JSON:
			out.println( output_JSON(sentence));
			break;
		case CONLL_X:
			out.println( output_CONLL(sentence, cmm));
			break;
		case XML:
			try {
				output_XML(sentence, out);
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
			break;
		case VISL_CG:
			out.println( output_VISL(sentence));
			break;
		case lemmatizedText:
			out.println( output_lemmatized(sentence));
			break;
        case lowercasedText:
            out.println( output_lowercased(sentence));
            break;
        case analyzerOptions:
            out.println( output_analyzer(sentence));
            break;
		default:
			out.println( output_separated(sentence));	    
		}
		if (outputSeparators) out.println("</s>");
		out.flush();
	}	
	
	private static String output_JSON(List<CoreLabel> tokens) {		
		LinkedList<String> tokenJSON = new LinkedList<String>();
		
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform maxwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false);
			if (mini_tag) maxwf.removeNonlexicalAttributes();
			if (maxwf != null)
				tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"%s\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(maxwf.getTag()), JSONValue.escape(maxwf.getValue(AttributeNames.i_Lemma))));
			else 
				tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"-\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(token)));			
		}
		
		String s = formatJSON(tokenJSON).toString();

		return s;
	}
	
	private static void output_XML(List<CoreLabel> tokens, PrintStream straume) throws IOException {
		PrintWriter w = new PrintWriter(straume);
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform maxwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false);
			if (mini_tag) maxwf.removeNonlexicalAttributes();
			maxwf.addAttribute("Tag", maxwf.getTag());
			maxwf.toXML(w);
//			if (maxwf != null)
//				tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"%s\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(maxwf.getTag()), JSONValue.escape(maxwf.getValue(AttributeNames.i_Lemma))));
//			else 
//				tokenJSON.add(String.format("{\"Word\":\"%s\",\"Tag\":\"-\",\"Lemma\":\"%s\"}", JSONValue.escape(token), JSONValue.escape(token)));			
		}		
		w.flush();
	}

	private static String output_CONLL(List<CoreLabel> tokens, CMMClassifier<CoreLabel> cmm){
		StringBuilder s = new StringBuilder();

		int counter = 1;
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			token = token.replace(' ', '_');
			
			s.append(Integer.toString(counter));
			s.append('\t');
			s.append(token);
			s.append('\t');
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform mainwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false); 
			if (mainwf != null) {
				String lemma = mainwf.getValue(AttributeNames.i_Lemma);
				lemma = lemma.replace(' ', '_');
				String answer = word.getString(AnswerAnnotation.class);
				if (answer.trim().isEmpty()) answer = "_"; // no empty tag
				s.append(lemma);
				s.append('\t');
				s.append(answer);
				s.append('\t');
				s.append(mainwf.getTag());
				s.append('\t');

				// Feature atribūtu filtri
				if (mini_tag) mainwf.removeNonlexicalAttributes();
				if (LETAfeatures) {
					addLETAfeatures(mainwf);
					// mainwf.removeAttribute(AttributeNames.i_SourceLemma); FIXME - atvasinātiem vārdiem šis var būt svarīgs, atpriedekļotas lemmas..
					mainwf.removeTechnicalAttributes();
				}
				
				s.append(mainwf.pipeDelimitedEntries()); // Pievienojam vārda fīčas
				
				if (features) { // visas fīčas, ko lietoja trenējot
					Datum<String, String> d = cmm.makeDatum(tokens, counter, cmm.featureFactory);
					for (String feature : d.asFeatures()) {
						s.append(feature.substring(0, feature.length()-2).replace(' ', '_')); // noņeam trailing |C kas tām fīčām tur ir
						s.append('|');
					}
				}
				s.deleteCharAt(s.length()-1); // noņemam peedeejo | separatoru, kas ir lieks
				s.append('\t');
				
			} else {
				s.append(token); 
				s.append("\t_\t_\t_\t");
			}
			if (saveColumns) {
				s.append(word.getString(ExtraColumnAnnotation.class));
			} else {
				String syntax = word.getString(ParentAnnotation.class);
				if (syntax != null) {
					s.append(syntax);
				}
				else s.append("_\t_\t_\t_");
			}
			s.append(eol);
			counter++;
		}
		
		return s.toString();
	}
	
	private static String output_lemmatized(List<CoreLabel> tokens){
		StringBuilder s = new StringBuilder();
		
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			token = token.replace(' ', '_');
			
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform mainwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false); 
			if (mainwf != null && !token.isEmpty()) {
				String lemma = mainwf.getValue(AttributeNames.i_Lemma);
				if (saveCase && Character.isUpperCase(token.charAt(0))) lemma = lemma.substring(0,1).toUpperCase() + lemma.substring(1);
				if (!saveCase) lemma=lemma.toLowerCase();
				lemma = lemma.replace(' ', '_');
				s.append(lemma);
				s.append(' ');
				
			} else {
				System.err.println("Empty lemma");
			}
		}
		return s.toString().trim();
	}

    private static String output_lowercased(List<CoreLabel> tokens){
        StringBuilder s = new StringBuilder();

        for (CoreLabel word : tokens) {
            String token = word.getString(TextAnnotation.class);
            if (token.contains("<s>")) continue;
            token = token.replace(' ', '_').toLowerCase();
            s.append(token);
            s.append(' ');
        }
        return s.toString().trim();
    }

    private static String output_analyzer(List<CoreLabel> tokens){
        StringBuilder s = new StringBuilder();

        for (CoreLabel word : tokens) {
            String token = word.getString(TextAnnotation.class);
            if (token.contains("<s>")) continue;
            token = token.replace(' ', '_');
            s.append(token);
            Word analysis = word.get(LVMorphologyAnalysis.class);
            for (Wordform wf : analysis.wordforms) {
                s.append(token_separator);
                s.append(wf.getTag());
            }
        }
        return s.toString().trim();
    }

	private static void addLETAfeatures(Wordform wf) {
		String lemma = wf.getValue(AttributeNames.i_Lemma);
				
		if (wf.isMatchingStrong(AttributeNames.i_PartOfSpeech, AttributeNames.i_Number)) {
			String numbercode = lemma.replaceAll("\\d", "0"); // uzskatam ka nav atšķirības starp skaitļiem ja ciparu skaits vienāds
			wf.addAttribute("LETA_lemma", numbercode);
		} else if (wf.isMatchingStrong(AttributeNames.i_CapitalLetters, AttributeNames.v_FirstUpper) && Dictionary.dict("surnames").contains(lemma))
			wf.addAttribute("LETA_lemma", "_surname_");
		else if (Dictionary.dict("vocations").contains(lemma))
			wf.addAttribute("LETA_lemma", "_vocation_");
		else if (Dictionary.dict("relations").contains(lemma))
			wf.addAttribute("LETA_lemma", "_relationship_");
		else if (Dictionary.dict("partijas").contains(lemma))
			wf.addAttribute("LETA_lemma", "_party_"); // TODO - nočekot kā visā procesā sanāk ar case-sensitivity, te tas ir svarīgi
		else if (Dictionary.dict("months").contains(lemma)) // TODO - te būtu jāčeko, lai personvārdi Marts un Jūlijs te neapēdas, ja ir ar lielo burtu ne teikuma sākumā 
			wf.addAttribute("LETA_lemma", "_month_"); 
		else if (Dictionary.dict("common_lemmas").contains(lemma)) 
			wf.addAttribute("LETA_lemma", lemma);
		else wf.addAttribute("LETA_lemma", "_rare_");		
	}

	// VISL CG format, as described in http://beta.visl.sdu.dk/cg3/chunked/streamformats.html#stream-vislcg
	private static String output_VISL(List<CoreLabel> tokens) {		
		StringBuilder s = new StringBuilder();
		
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
			
			token.replaceAll("\"", "\\\""); // VISL (seems to) require to escape quotes in their format. Possibly other escaping needs, not sure from their docs.
			
			s.append(String.format("\"<%s>\"\n", token)); // <"They"> from the example
			
			Word analysis = word.get(LVMorphologyAnalysis.class);
			Wordform maxwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false);
			for (Wordform wf : analysis.wordforms) { // output the "cohort" in VISL-CG terms
				String lemma = wf.getValue(AttributeNames.i_Lemma);
				//Ad-hoc ... removing 'bookkeeping' attributes that seem useless for CG
				wf.removeTechnicalAttributes();
				wf.removeAttribute(AttributeNames.i_Lemma);
				wf.removeAttribute(AttributeNames.i_SourceLemma);
				
				lemma.replaceAll("\"", "\\\"");
				s.append(String.format("\t\"%s\" ", lemma)); // <"They"> from the example
				s.append(wf.getTag());
				s.append(" ");
				AttributeValues minimum = new AttributeValues(wf);
				minimum.removeNonlexicalAttributes();
				s.append(minimum.getTag());
				s.append(" ");
				for (Entry<String, String> entry : wf.entrySet()) { // visi attributevalue paariishi
					String key = entry.getKey();
					String value = entry.getValue();
					// For attributes with distinctive value names (like parts of speech) skip the attribute name for readability in CG
					if ((!key.equalsIgnoreCase(AttributeNames.i_PartOfSpeech) && 
						!key.equalsIgnoreCase(AttributeNames.i_Case) && 
						!key.equalsIgnoreCase(AttributeNames.i_Number) &&
						!key.equalsIgnoreCase(AttributeNames.i_Gender) &&
						!key.equalsIgnoreCase(AttributeNames.i_NounType) &&
						!key.equalsIgnoreCase(AttributeNames.i_Izteiksme) &&
						!key.equalsIgnoreCase(AttributeNames.i_VerbType) &&
						!key.equalsIgnoreCase(AttributeNames.i_Laiks) &&
						!key.equalsIgnoreCase(AttributeNames.i_Transitivity) &&
						!key.equalsIgnoreCase(AttributeNames.i_Declension) &&						
						!key.equalsIgnoreCase(AttributeNames.i_Definiteness) &&
						!key.equalsIgnoreCase(AttributeNames.i_Lokaamiiba) &&
						!key.equalsIgnoreCase(AttributeNames.i_AdjectiveType) &&
						!key.equalsIgnoreCase(AttributeNames.i_SaikljaTips) &&
						!key.equalsIgnoreCase(AttributeNames.i_Uzbuuve) &&
						!key.equalsIgnoreCase(AttributeNames.i_PieturziimesTips) &&
						!key.equalsIgnoreCase(AttributeNames.i_Voice) &&
						!key.equalsIgnoreCase(AttributeNames.i_VvTips)
						) || (value.equalsIgnoreCase(AttributeNames.v_NA) &&
								!key.equalsIgnoreCase(AttributeNames.i_Anafora) &&
								!key.equalsIgnoreCase(AttributeNames.i_Laiks)
						)) {
						 s.append(key.replace(' ', '_')); 
						 s.append('=');
					}
					 s.append(value.replace(' ', '_'));
					 s.append(' ');
				}
				s.append(eol);
			}
		}
		
		s.append("\"<<s>>\"");
		
		return s.toString();
	}
	
	private static String output_separated(List<CoreLabel> tokens){
		StringBuilder s = new StringBuilder();
		
		for (CoreLabel word : tokens) {
			String token = word.getString(TextAnnotation.class);
			if (token.contains("<s>")) continue;
            Word analysis = word.get(LVMorphologyAnalysis.class);
            Wordform mainwf = analysis.getMatchingWordform(word.getString(AnswerAnnotation.class), false);

            if (s.length()>0) s.append(token_separator);
            if (whitespaceMarker && mainwf.isMatchingStrong(AttributeNames.i_WhitespaceBefore, "")) {
                s.append("<g />");
                s.append(token_separator);
            }

            if (outputType == outputTypes.MOSES) token = token.replace(' ', '_');
			s.append(token);
			s.append(field_separator);

			if (mainwf != null) {
				if (mini_tag) mainwf.removeNonlexicalAttributes();
				s.append(mainwf.getTag());
				s.append(field_separator);
				String lemma = mainwf.getValue(AttributeNames.i_Lemma);
				if (outputType == outputTypes.MOSES) lemma = lemma.replace(' ', '_');
				s.append(lemma);
			} else s.append(field_separator); 
			/*
			mainwf = word.get(LVMorphologyAnalysisBest.class);
			if (mainwf != null) {
				s.append("Single-token suggestion:\t");
				s.append(mainwf.getTag());
				s.append("\t");
				s.append(mainwf.getValue(AttributeNames.i_Lemma));
				s.append("\t");
			}
			s.append("\n");
			if (all_options)
					s.append(word.toTabSep(statistics, probabilities));
			else s.append(word.toTabSepsingle(statistics)); */
		}
		
		tokens = null;
		return s.toString();
	}
	
	private static StringBuilder formatJSON(Collection<String> tags) {
		Iterator<String> i = tags.iterator();
		StringBuilder out = new StringBuilder("[");
		while (i.hasNext()) {
			out.append(i.next());
			if (i.hasNext()) out.append(", ");
		}
		out.append("]");
		return out;
	}
	
	
	public static List<List<CoreLabel>> readCONLL(BufferedReader in) throws IOException {
		String s;
	    List<CoreLabel> sentence = new LinkedList<CoreLabel>();
	    List<List<CoreLabel>> result = new LinkedList<List<CoreLabel>>();
	    
	    CoreLabel stag = new CoreLabel();
		stag.set(TextAnnotation.class, "<s>");
		sentence.add(stag);
	    
	    while ((s = in.readLine()) != null) {
	    	if (s.trim().length() > 0) {
	    		String[] fields = s.split("\t");
	    		String token = fields[1];
	    		if (!token.equalsIgnoreCase("_")) token = token.replace('_', ' ');
	    		String extraColumns = "";
	    		if (saveColumns) {
	    			for (int field_i = 6; field_i < fields.length; field_i++) extraColumns += fields[field_i] + "\t";
	    			extraColumns.trim();
	    		}	    		
	    		String syntax = "";
	    		if (fields.length >= 10) syntax = fields[6] + "\t" + fields[7] + "\t" + fields[8] + "\t" + fields[9];

	    		CoreLabel word = new CoreLabel();
				word.set(TextAnnotation.class, token);
				word.set(ParentAnnotation.class, syntax);
				word.set(ExtraColumnAnnotation.class, extraColumns);
	    		sentence.add(word);
	    	} else {
	    		stag = new CoreLabel();
	    		stag.set(TextAnnotation.class, "<s>");
	    		sentence.add(stag);
	    		
	    		result.add(LVMorphologyReaderAndWriter.analyzeLabels(sentence));
	    		
	    		sentence = new LinkedList<CoreLabel>();
	    		stag = new CoreLabel();
	    		stag.set(TextAnnotation.class, "<s>");
	    		sentence.add(stag);
	    	}
	    }
	    if (sentence.size() > 0) {
	    	stag = new CoreLabel();
			stag.set(TextAnnotation.class, "<s>");
			sentence.add(stag);
	    	result.add(LVMorphologyReaderAndWriter.analyzeLabels(sentence));
	    }
	    		
		return result;
	}

}	
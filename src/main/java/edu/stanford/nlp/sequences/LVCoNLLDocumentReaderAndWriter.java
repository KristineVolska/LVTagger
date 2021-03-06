/*******************************************************************************
 * Copyright 2013,2014 Institute of Mathematics and Computer Science, University of Latvia
 * Author: Artūrs Znotiņš
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
package edu.stanford.nlp.sequences;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.io.PrintWriter;
import java.io.Reader;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.regex.Pattern;

import edu.stanford.nlp.io.RuntimeIOException;
import edu.stanford.nlp.ling.CoreAnnotations.AnswerAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.AnswersAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.DistSimAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ExtraColumnAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVFullTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVGazAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LVGazFileAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.LabelAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.MorphologyFeatureStringAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.NamedEntityTagGoldAnnotation;
import edu.stanford.nlp.ling.CoreAnnotations.ParentAnnotation;
import edu.stanford.nlp.ling.CoreLabel;
import edu.stanford.nlp.objectbank.DelimitRegExIterator;
import edu.stanford.nlp.objectbank.IteratorFromReaderFactory;
import edu.stanford.nlp.util.Function;
import edu.stanford.nlp.util.StringUtils;


public class LVCoNLLDocumentReaderAndWriter implements DocumentReaderAndWriter<CoreLabel> {

  private static final long serialVersionUID = 3806263423697973704L;

  private IteratorFromReaderFactory<List<CoreLabel>> factory;
  
  public static final String BOUNDARY = "<s>";
  public static final String OTHER = "O";
  private SeqClassifierFlags flags;

  /**
   * CONLL : 		IDX | WORD | LEMMA | FULLTAG | POS | MORPHOFEATURES | NER
   * SIMPLE : 		WORD | GOLD | ANSWER
   * INFEATURES : 	IDX | WORD | LEMMA | FULLTAG | POS | MORPHOFEATURES+ner=X
   */
  public enum outputTypes {SIMPLE, CONLL, INFEATURES, COMPARE};
  public enum inputTypes {CONLL};
  
  //TODO make theese more clean and configureable
  public static outputTypes outputType = outputTypes.CONLL;
  public static inputTypes inputType = inputTypes.CONLL;
  public static boolean saveExtraColumns = false;
  public static int saveExtraColumnsFrom = 9; // including
  
  private static final String eol = System.getProperty("line.separator");

  
  public void init(SeqClassifierFlags flags) {
	  this.flags = flags;
	  factory = DelimitRegExIterator.getFactory("\n(?:\\s*\n)+", new ConlllDocParser());
  }


  public Iterator<List<CoreLabel>> getIterator(Reader r) {
    return factory.getIterator(r);
  }

  private int num; // = 0;


  private class ConlllDocParser implements Serializable, Function<String,List<CoreLabel>> {

    private static final long serialVersionUID = -6266332661459630572L;
    private final Pattern whitePattern = Pattern.compile("\\s+");

    int lineCount = 0;

    public List<CoreLabel> apply(String doc) {
      if (num > 0 && num % 1000 == 0) { System.err.print("["+num+"]"); }
      num++;

      List<CoreLabel> words = new ArrayList<CoreLabel>();

      String[] lines = doc.split("\n");

      for (String line : lines) {
        ++lineCount;
//        if (line.trim().length() == 0) {
//          continue;
//        }
        //String[] info = whitePattern.split(line);
        // todo: We could speed things up here by having one time only having converted map into an array of CoreLabel keys (Class<? extends CoreAnnotation<?>>) and then instantiating them. Need new constructor.
        CoreLabel wi;
        try {
          wi = makeCoreLabel(line);
        } catch (RuntimeException e) {
          System.err.println("Error on line " + lineCount + ": " + line);
          throw e;
        }
        words.add(wi);
      }
      return words;
    }
  }


  
//  private List<CoreLabel> processDocument(String doc) {
//	    List<CoreLabel> lis = new ArrayList<CoreLabel>();
//	    String[] lines = doc.split(eol);
//	    for (String line : lines) {
//	      if ( !flags.deleteBlankLines || ! white.matcher(line).matches()) {
//	        lis.add(makeCoreLabel(line));
//	      }
//	    }
//	    return lis;
//	  }


	  /** 
	   *
	   *  @param line A line of CoNLL input
	   *  @return The constructed token
	   */
	  private CoreLabel makeCoreLabel(String line) {
	    CoreLabel wi = new CoreLabel();
	    line = line.trim();				//TODO empty simple morpho tag fix
	    String[] bits = line.split("\t");//String[] bits = line.split("\\s+"); 
	    if (bits.length <= 1) {
	    	wi.setWord(BOUNDARY);
	        wi.set(AnswerAnnotation.class, OTHER);
	        wi.set(NamedEntityTagGoldAnnotation.class, OTHER);
	        wi.setLemma("_");
	    } else if (bits.length >= 6) {
	    	//conll-x format produced by morphotagger
	    	wi.setIndex(Integer.parseInt(bits[0]));
	    	wi.setWord(bits[1]);
	    	wi.setLemma(bits[2]);
	    	wi.set(LVFullTagAnnotation.class, bits[4]);
	    	String tag = "";
	    	if (!bits[4].isEmpty()) tag = bits[4].substring(0,1); 
	    	wi.setTag(tag);
	    	wi.set(MorphologyFeatureStringAnnotation.class, bits[5]);
	    	//wi.set(ParentAnnotation);
	    	if (bits.length >= 7) {
	    		//syntax
	    		wi.set(ParentAnnotation.class, bits[6]);
	    	}
	    	if (bits.length >= 8) {
	    		wi.set(LabelAnnotation.class, bits[7]);
	    	}
	    	if (bits.length >= 9) {
	    		wi.set(NamedEntityTagGoldAnnotation.class, bits[8]);
	    	}
	    	if (saveExtraColumns && bits.length >= saveExtraColumnsFrom) {
	    		StringBuilder extraColumns = new StringBuilder();
	    		for (int i=saveExtraColumnsFrom; i < bits.length; i++) {
	    			extraColumns.append(bits[i]).append("\t");
	    		}
	    		wi.set(ExtraColumnAnnotation.class, extraColumns.toString());
	    	}	    	
	    } else {
	    	throw new RuntimeIOException("Unexpected conll input (field count) " + line);
	    }
	    return wi;
	  }

	  private String intern(String s) {
	    if (flags.intern) {
	      return s.intern();
	    } else {
	      return s;
	    }
	  }

	  public void printAnswers(List<CoreLabel> doc, PrintWriter out) {
		for (CoreLabel fl : doc) {
	      String word = fl.word();
	      if (word == BOUNDARY) {
	        out.println();
	      } else {
	        String goldAnswer = fl.get(NamedEntityTagGoldAnnotation.class); //fl.get(GoldAnswerAnnotation.class);
	        String answer = fl.get(NamedEntityTagAnnotation.class);
	        if (answer == null) {
	        	answer = fl.get(AnswerAnnotation.class);
	        }
	        //System.out.println(fl);
	        String tag = fl.tag();
	        String lemma = fl.lemma();
	        String fullTag = fl.getString(LVFullTagAnnotation.class);
	        String morphoFeats = fl.getString(MorphologyFeatureStringAnnotation.class);
	        if (fl.get(DistSimAnnotation.class) != null) morphoFeats += "|Distsim=" + fl.getString(DistSimAnnotation.class);
	        if (fl.get(LVGazAnnotation.class) != null && fl.get(LVGazAnnotation.class).size() > 0) morphoFeats += "|Gaz=" + StringUtils.join(fl.get(LVGazAnnotation.class), ",");
	        if (fl.get(LVGazFileAnnotation.class) != null && fl.get(LVGazFileAnnotation.class).size() > 0) morphoFeats += "|GazFile=" + StringUtils.join(fl.get(LVGazFileAnnotation.class), ",");
	        
	        if (outputType == outputTypes.CONLL) {
	        	out.print(fl.index() + "\t" + word + '\t' + lemma + '\t' + tag + '\t' + 
		        		fullTag + '\t' + morphoFeats);
	        	if (fl.get(ParentAnnotation.class) != null) out.print('\t' + fl.getString(ParentAnnotation.class)); 
	        	else out.print("\t_");
	        	if (fl.get(LabelAnnotation.class) != null) out.print('\t' + fl.getString(LabelAnnotation.class)); 
	        	else out.print("\t_");
	        	out.print('\t' + answer);
	        	if (saveExtraColumns) out.print("\t" + fl.getString(ExtraColumnAnnotation.class));
	        	out.println();
	        } else if (outputType == outputTypes.SIMPLE){
	        	out.print(word + "\t" + goldAnswer + "\t" + answer);
	        	if (saveExtraColumns) out.print("\t" + fl.getString(ExtraColumnAnnotation.class));
	        	out.println();
	        } else if (outputType == outputTypes.INFEATURES) {
	        	out.print(fl.index() + "\t" + word + '\t' + lemma + '\t' + tag + '\t' + 
		        		fullTag + '\t' + morphoFeats + "|ner=" + answer);
	        	if (saveExtraColumns) out.print("\t" + fl.getString(ExtraColumnAnnotation.class));
	        	out.println();
	        } else if (outputType == outputTypes.COMPARE){
	        	out.print(word + "\t" + goldAnswer);
	        	for (String a : fl.get(AnswersAnnotation.class)) out.print("\t" + a);
	        	if (saveExtraColumns) out.print("\t" + fl.getString(ExtraColumnAnnotation.class));
	        	out.println();
	        }
	      }
	    }
		out.flush();
	  } 
	  
	public List<CoreLabel> readCONLL(String filename) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(filename));		
		List<CoreLabel> doc = readCONLL(in);
		in.close();
		return doc;
	}
	
	/**
	 * Read conll input. Stop after 3 blank lines or EOL reached.
	 * @param is
	 * @return
	 * @throws IOException
	 */
	public List<CoreLabel> readCONLL(BufferedReader is) throws IOException {
		List<CoreLabel> res = new ArrayList<>();
		String line;
		int blankLines = 0;
		while ((line = is.readLine()) != null) {
			if (line.trim().equals("")) {
				++blankLines;
				if (blankLines > 3) {
					break;
				}
			} else {
				blankLines = 0;
			}
			res.add(makeCoreLabel(line));
		}
		return res;
	}
}

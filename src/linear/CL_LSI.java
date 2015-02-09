package linear;

import data.PreProcessTerm;
import data.TokenType;
import data.Channel;
import data.SentFile;

import es.upv.nlel.utils.Language;

import common.Dictionary;
import common.Corpus;
import common.Sentence;

import java.util.Set;
import java.util.HashSet;
import java.util.List;
import java.util.ArrayList;
import java.io.PrintWriter;
import java.io.IOException;
import java.io.File;

import math.SparseMatrix;
import org.jblas.FloatMatrix;
import org.jblas.Eigen;



public class CL_LSI {
  Dictionary dict;
  Corpus parallelCorp;
  FloatMatrix C;
  FloatMatrix[] eigen;
  FloatMatrix projectionMatrix;
  int[] tf;
  int[] df;
  // load dictionaries for both
  // load data with corpus class for both
  // create a parallel corpus class where you iterate over sentence list for both of them and retain certain sentences
  
  // create document-term matrix (D) (nxk)
  // create correlation matrix C = D^T*D (kxk)
  // get eigen vetors - projection matrix
  // project documents
  // project queries
  // retrieve
  
  /** It would merge the dictionaries and create the joint for this model.*/
  public void loadDictionary(String dict1File, String dict2File) throws IOException {
    this.dict = new Dictionary();
    this.dict.load(dict1File);
    
    Dictionary dict2 = new Dictionary();
    dict2.load(dict2File);

    this.dict.mergeDict(dict2);
  }

  public Corpus loadCorpus(String file, Channel ch) throws IOException {
    Corpus corp = new Corpus();
    corp.load(file, false, ch, this.dict, false);
    return corp; 
  }

  public void calculateStats() {
    this.tf = new int[this.dict.getSize()];
    this.df = new int[this.dict.getSize()];
    for(int i=0; i< this.parallelCorp.getSize(); i++) {
      Sentence s = this.parallelCorp.get(i);
      Set<Integer> uniqueTerms = new HashSet<Integer>();
      for(int j=0; j<s.getSize(); j++) {
        tf[s.get(j)]++;
        uniqueTerms.add(s.get(j));
      }
      for(int j: uniqueTerms)
        df[j]++;
    }
  }

  /** It will us the joint dictionary to create a parallel corpus of type Corpus with certain parameters*/
  public void prepareParallelCorpus(Corpus corp1, Corpus corp2, int minLength, int maxSize) throws IOException {
    this.parallelCorp = new Corpus();
    assert (corp1.getSize() == corp2.getSize()):System.out.printf("Both Corpora should have same length. Currently lengths, Corp1 = %d and Corp2 = %d", corp1.getSize(), corp2.getSize());
    int count = 0;
    PrintWriter pEn = new PrintWriter("data/fire/joint/subparallel-en.dat", "UTF-8");
    PrintWriter pEnTest = new PrintWriter("data/fire/joint/subparallel-en-test.dat", "UTF-8");
    
    PrintWriter pHi = new PrintWriter("data/fire/joint/subparallel-hi.dat", "UTF-8");
    PrintWriter pHiTest = new PrintWriter("data/fire/joint/subparallel-hi-test.dat", "UTF-8");

    for(int i=0; i<corp1.getSize(); i++) {
      Sentence s = corp1.get(i);
      Sentence s2 = corp2.get(i);
      if(s.getSize()>=minLength && s2.getSize()>=minLength && count<maxSize) {
        s.addSent(s2);
        if(count < maxSize) {
          this.parallelCorp.addSent(s);
          pEn.println(corp1.get(i).toString());
          pHi.println(corp2.get(i).toString());
          count++;
        }
        else {
          pEnTest.println(corp1.get(i).toString());
          pHiTest.println(corp2.get(i).toString());
        }

      }
    }

    pEn.close();
    pEnTest.close();

    pHi.close();
    pHiTest.close();
    System.out.printf("Parallel corpus created with size = %d\n", this.parallelCorp.getSize());
  }

  public void createCorrelationMatrix() {
    FloatMatrix d = FloatMatrix.zeros(this.parallelCorp.getSize(), this.dict.getSize());
    System.out.printf("Matrix D dim = %d x %d\n", d.rows, d.columns);
    for(int i=0; i< this.parallelCorp.getSize(); i++) {
      Sentence s = this.parallelCorp.get(i);
//      FloatMatrix vec = FloatMatrix.zeros(1, this.dict.getSize());
      for(int j=0; j<s.getSize(); j++) {
//        System.out.printf("indices (%d ,%d)\n", i, s.get(j));
//        vec.put(s.get(j), (float)1.0);
//        int index = d.columns*i+s.get(j);
//        d.put(index, 1.0);
        d.put(i, s.get(j), (float)1.0);
      }
//      d.putRow(i, vec);
    }
    this.C = d.transpose().mmul(d);
    d = new FloatMatrix(1, 1);
  }

  public void createSparseDMatrix(File f) throws IOException {
    SparseMatrix mat = new SparseMatrix(this.parallelCorp.getSize(), this.dict.getSize());
    System.out.printf("Matrix D dim = %d x %d\n", mat.rows(), mat.columns());

    int N = this.parallelCorp.getSize();
    for(int i=0; i< this.parallelCorp.getSize(); i++) {
      Sentence s = this.parallelCorp.get(i);
      for(int j=0; j<s.getSize(); j++) {
        double v = (Math.log(1.0+this.tf[s.get(j)])/Math.log(2.0))*(Math.log(N/this.df[s.get(j)])/Math.log(2.0));
        mat.put(i, s.get(j), v);
      }
    }
    mat.print(f);
  }

  public void learnProjectionMatrix(int dim) {
    this.eigen = Eigen.symmetricEigenvectors(this.C);
    int[] cols = new int[dim];
    for(int i=0; i<dim; i++)
      cols[i]=i;
    this.projectionMatrix = this.eigen[0].getColumns(cols);
    System.out.printf("Size of the projection matrix = %d x %d\n", this.projectionMatrix.rows, this.projectionMatrix.columns);
  }



  public static void main(String[] args) throws IOException {
    CL_LSI model = new CL_LSI();

    Language lang1 = Language.EN;
    Language lang2 = Language.HI;

    String dict1File = "data/fire/en/dict-100.txt"; // en
    String dict2File = "data/fire/hi/dict-400.txt"; // hi

    String enFile = "data/fire/en/train.low.eng.sub";
    String hiFile = "data/fire/hi/train.low.hin.sub";


    model.loadDictionary(dict1File, dict2File);

    String path_to_terrier = "/home/parth/workspace/terrier-3.5/";
    List<PreProcessTerm> pipeline = new ArrayList<PreProcessTerm>();
    pipeline.add(PreProcessTerm.SW_REMOVAL);
    pipeline.add(PreProcessTerm.STEM);

    Channel chEn = new SentFile(enFile);
    chEn.setup(TokenType.WORD, lang1, path_to_terrier, pipeline);
    Corpus enCorp = model.loadCorpus(enFile, chEn);

    Channel chHi = new SentFile(hiFile);
    chHi.setup(TokenType.WORD, lang2, path_to_terrier, pipeline);
    Corpus hiCorp = model.loadCorpus(hiFile, chHi);

    model.prepareParallelCorpus(enCorp, hiCorp, 3, 100000);
    chEn = new SentFile(enFile);
    chHi = new SentFile(hiFile);
    System.gc(); System.gc();
    System.gc(); System.gc();

    // TODO: Prepare stats like TF and DF
    model.calculateStats();
    model.createSparseDMatrix(new File("data/fire/joint/D.dat"));
    model.dict.save("data/fire/joint/dict.txt");
//    model.createCorrelationMatrix();
//    model.learnProjectionMatrix(128);
  }
}
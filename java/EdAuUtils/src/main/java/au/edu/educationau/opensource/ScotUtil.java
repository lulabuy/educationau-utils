package au.edu.educationau.opensource;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.io.IOUtils;
import org.jdom.Document;
import org.jdom.Element;
import org.jdom.input.SAXBuilder;
import org.jdom.xpath.XPath;

/**
 * Wrapper around a Scot file (or the file contents from some other input stream) giving basic search functionality. When an instance of this class is created
 * using one of the create methods, it reads the given Scot data and creates an in memory copy ({@link Term}). The instance has util methods to search the set
 * of terms.
 * 
 * Usage example:
 *		ScotUtil scotUtil = ScotUtil.create("Scotv61.txt");
 *		List<Term> someTerms = scotUtil.findTermsStartingWith("an");
 *		Term someTerm = scotUtil.findTerm("Animal husbandry");
 */
public class ScotUtil {
	private Map<String, Term> termsByName;
	private Map<String, Term> termsByNameCaseInsensitive;
	
	private Map<Integer, Term> termsByNumber;

	private List<Term> terms;

	
	@SuppressWarnings("unchecked")
	private ScotUtil(Map<String, Term> termsByName) {
		this.termsByName = termsByName;

		terms = new ArrayList(termsByName.values());
		Collections.sort(terms, new Comparator<Term>() {
			public int compare(Term t1, Term t2) {
				return t1.name.compareTo(t2.name);
			}
		});

		termsByNumber = new HashMap<Integer, Term>();
		termsByNameCaseInsensitive = new HashMap<String, Term>();
		for (Term term : terms) {
			termsByNumber.put(term.termNumber, term);
			termsByNameCaseInsensitive.put(term.name.toLowerCase(), term);
		}
	};

	/**
	 * Finds all terms with termName starting with the given prefix (case insensitive). Returned in alphabetical order.
	 */
	public List<Term> findTermsStartingWith(String prefix) {
		List<Term> results = new ArrayList<Term>();
		for (Term term : terms) {
			if (term.name.toLowerCase().startsWith(prefix.toLowerCase())) {
				results.add(term);
			}
		}
		return results;
	}

	/**
	 * Finds all terms with termName containing the given prefix (case insensitive). Returned in alphabetical order.
	 */
	public List<Term> findTermsContaining(String prefix) {
		List<Term> results = new ArrayList<Term>();
		for (Term term : terms) {
			if (term.name.toLowerCase().contains(prefix.toLowerCase())) {
				results.add(term);
			}
		}
		return results;
	}

	/**
	 * Finds the term with given termName (case sensitive).
	 */
	public Term findTerm(String termName) {
		return termsByName.get(termName);
	}
	
	public Term findTermCaseInsensitive(String termName) {
		return termsByNameCaseInsensitive.get(termName.toLowerCase());
	}

	/**
	 * Finds the term with given termNumber.
	 */
	public Term findTermByNumber(Integer termNumber) {
		return termsByNumber.get(termNumber);
	}

	
	
	/**
	 * Creates an instance from the file specified by the given filePath.
	 */
	public static ScotUtil create(String filePath) {
		try {
			FileInputStream in = new FileInputStream(filePath);
			try {
				return create(in);
			} finally {
				in.close();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	/**
	 * Creates an instance from the given input stream. Takes < 1 second of CPU on a 2.4Ghz P4.
	 */
	public static ScotUtil create(InputStream scotFileDataInputStream) {
		String scotData = null;
		try {
			scotData = IOUtils.toString(scotFileDataInputStream);
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
		try {
			// terms are delimited by double line break, this array will contain 1 term's data per element
			String[] termDataStrings = scotData.split("\r\n\r\n|\r\r|\n\n");

			Map<Term, Map<String, List<String>>> termData = new HashMap<Term, Map<String, List<String>>>();
			Map<String, Term> termsByName = new HashMap<String, Term>();

			for (String termDataString : termDataStrings) {
				String[] dataLines = termDataString.split("\r\n|\r|\n"); // each field value for a term is on a new line

				Term term = new Term();
				term.name = dataLines[0]; // name is always first
				termsByName.put(term.name, term);

				Map<String, List<String>> termFields = new HashMap<String, List<String>>();
				
				// some fields are repeatable, the field name occurs for the first value but is suppressed for the repeated lines/values -
				// this loop will re-use the most recently seen field name if a line doesn't have a field name
				String currentFieldName = null;
				for (int i = 1; i < dataLines.length; i++) {
					String dataLine = dataLines[i];
					String fieldValue = null;
					if (currentFieldName == null || dataLine.matches("^\\s*?[A-Z]{2,4}:.*")) {
						currentFieldName = dataLine.replaceAll("^\\s*?([A-Z]{2,4}):.*", "$1");
						fieldValue = dataLine.replaceAll("^.*?:", "").trim();
					} else {
						fieldValue = dataLine.trim();
					}

					// append the value to the current list of values (or a new list of none exists) for the current field name
					List<String> fieldValues = termFields.get(currentFieldName);
					if (fieldValues == null) {
						fieldValues = new ArrayList<String>();
						termFields.put(currentFieldName, fieldValues);
					}
					fieldValues.add(fieldValue);
				}
				termData.put(term, termFields);
			}

			for (Term term : termData.keySet()) {
				Map<String, List<String>> data = termData.get(term);
				term.scopeNote = data.get("SN") != null ? data.get("SN").get(0) : null;
				term.termNumber = Integer.parseInt(data.get("TNR").get(0));
				if (data.get("USE") != null) {
					for (String termName : data.get("USE")) {
						term.use.add(termsByName.get(termName));
					}
				}
				if (data.get("UF") != null) {
					for (String termName : data.get("UF")) {
						term.usedFor.add(termsByName.get(termName));
					}
				}
				if (data.get("BT") != null) {
					for (String termName : data.get("BT")) {
						term.broaderTerms.add(termsByName.get(termName));
					}
				}
				if (data.get("NT") != null) {
					for (String termName : data.get("NT")) {
						term.narrowerTerms.add(termsByName.get(termName));
					}
				}
				if (data.get("RT") != null) {
					for (String termName : data.get("RT")) {
						term.relatedTerms.add(termsByName.get(termName));
					}
				}
			}

			return new ScotUtil(termsByName);
		} catch (Exception e) {
			throw new RuntimeException("Error parsing Scot data - please check underlying data file is valid", e);
		}
	}
	
	/** @see #create(String) */
	public static ScotUtil createFromXml(String filePath) {
		try {
			FileInputStream in = new FileInputStream(filePath);
			try {
				return createFromXml(in);
			} finally {
				in.close();
			}
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}
	
	/** @see #create(InputStream)) */
	public static ScotUtil createFromXml(InputStream scotFileDataInputStream) {
		try {
			Map<String, Term> termsByName = new HashMap<String, Term>();

			Document document = new SAXBuilder(false).build(scotFileDataInputStream);
			List<Element> conceptElements = XPath.selectNodes(document, "/THESAURUS/CONCEPT");
			
			for (Element conceptElement : conceptElements) {
				Term term = new Term();
				
				term.name = conceptElement.getChild("DESCRIPTOR") != null ? conceptElement.getChildText("DESCRIPTOR") : conceptElement.getChildText("NON-DESCRIPTOR");
				term.termNumber = Integer.parseInt(conceptElement.getChildText("TNR"));
				term.scopeNote = conceptElement.getChildText("SN");
				termsByName.put(term.name, term);
			}
			
			for (Element conceptElement : conceptElements) {
				String termName = conceptElement.getChild("DESCRIPTOR") != null ? conceptElement.getChildText("DESCRIPTOR") : conceptElement.getChildText("NON-DESCRIPTOR");
				Term term = termsByName.get(termName);
				
				for (Element useElement : new ArrayList<Element>(conceptElement.getChildren("USE"))) {
					term.use.add(termsByName.get(useElement.getText()));
				}
				
				for (Element usedForElement : new ArrayList<Element>(conceptElement.getChildren("UF"))) {
					term.usedFor.add(termsByName.get(usedForElement.getText()));
				}
				
				for (Element broaderTermElement : new ArrayList<Element>(conceptElement.getChildren("BT"))) {
					term.broaderTerms.add(termsByName.get(broaderTermElement.getText()));
				}
				
				for (Element narrowerTermElement : new ArrayList<Element>(conceptElement.getChildren("NT"))) {
					term.narrowerTerms.add(termsByName.get(narrowerTermElement.getText()));
				}
				
				for (Element relatedTermElement : new ArrayList<Element>(conceptElement.getChildren("RT"))) {
					term.relatedTerms.add(termsByName.get(relatedTermElement.getText()));
				}
			}

			return new ScotUtil(termsByName);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static class Term {
		private Integer termNumber; // mandatory
		private String name; // mandatory

		private String scopeNote; // often null

		private List<Term> use = new ArrayList<Term>() ; // if not empty, this term is "non-preferred"

		private List<Term> usedFor = new ArrayList<Term>(); // references to non-preferred terms that this term replaces
		private List<Term> broaderTerms = new ArrayList<Term>(); // parent terms
		private List<Term> narrowerTerms = new ArrayList<Term>(); // child terms
		private List<Term> relatedTerms = new ArrayList<Term>();

		@Override
		public String toString() {
			return "Name: " + name + "\n" + "Number: " + termNumber + "\n" + "Scope note: " + scopeNote + "\n";
		}

		public Integer getTermNumber() {
			return termNumber;
		}

		public void setTermNumber(Integer termNumber) {
			this.termNumber = termNumber;
		}

		public String getName() {
			return name;
		}

		public void setName(String name) {
			this.name = name;
		}

		public String getScopeNote() {
			return scopeNote;
		}

		public void setScopeNote(String scopeNote) {
			this.scopeNote = scopeNote;
		}
		
		public List<Term> getUse() {
			return use;
		}

		public void setUse(List<Term> use) {
			this.use = use;
		}
		
		public List<Term> getUsedFor() {
			return usedFor;
		}

		public void setUsedFor(List<Term> usedFor) {
			this.usedFor = usedFor;
		}

		public List<Term> getBroaderTerms() {
			return broaderTerms;
		}

		public void setBroaderTerms(List<Term> broaderTerms) {
			this.broaderTerms = broaderTerms;
		}

		public List<Term> getNarrowerTerms() {
			return narrowerTerms;
		}

		public void setNarrowerTerms(List<Term> narrowerTerms) {
			this.narrowerTerms = narrowerTerms;
		}

		public List<Term> getRelatedTerms() {
			return relatedTerms;
		}

		public void setRelatedTerms(List<Term> relatedTerms) {
			this.relatedTerms = relatedTerms;
		}
	}
}

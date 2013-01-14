package org.osehra.das.repo.bluebutton;

import java.sql.Timestamp;
import java.util.Calendar;
import java.util.Collections;
import java.util.GregorianCalendar;
import java.util.List;

import javax.jms.Message;
import javax.jms.MessageListener;
import javax.jms.TextMessage;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.osehra.das.BeanUtils;
import org.osehra.das.FormatTS;
import org.osehra.das.wrapper.nwhin.C32DocumentEntity;
import org.osehra.das.wrapper.nwhin.C32DocumentEntityFactory;
import org.osehra.das.wrapper.nwhin.WrapperResource;

/**
 * Message-driven bean (MDB) to call a <code>WrapperResource</code> to retrieve a C32.  
 * Uses a <code>C32DocumentEntityFactory</code> to create a 
 * <code>C32DocumentEntity</code> from the retrieved C32 document, and then uses a 
 * <code>C32DocumentDao</code> to save the <code>C32DocumentEntity</code> result.
 * @author Dept of VA
 *
 */
public class NwhinDataRetriever extends AbstractC32DaoAware implements MessageListener {
	protected WrapperResource _nwhinResource;
	protected FormatTS asyncMessageFormat;
	protected static Log logger = LogFactory.getLog(NwhinDataRetriever.class);
	protected C32DocumentEntityFactory documentFactory;
	
	/**
	 * 
	 * @return Formatter for an AsyncRetrieveMessage
	 */
	public FormatTS getAsyncMessageFormat() {
		return asyncMessageFormat;
	}

	/**
	 * 
	 * @param asyncMessageFormat Formatter for an AsyncRetrieveMessage
	 */
	public void setAsyncMessageFormat(FormatTS asyncMessageFormat) {
		this.asyncMessageFormat = asyncMessageFormat;
	}

	/**
	 * 
	 * @return Factory for a C32DocumentEntity
	 */
	public C32DocumentEntityFactory getDocumentFactory() {
		return documentFactory;
	}

	/**
	 * 
	 * @param documentFactory Factory for a C32DocumentEntity
	 */
	public void setDocumentFactory(C32DocumentEntityFactory documentFactory) {
		this.documentFactory = documentFactory;
	}

	/**
	 * 
	 * @param resource C32 retriever interface
	 */
	public void setWrapperResource(WrapperResource resource) {
		_nwhinResource = resource;
	}

	/**
	 * Retrieves the text from the JMS message, parses it into an AsyncRetrieveMessage, calls
	 * a WrapperResource for a C32 document, creates a C32DocumentEntity from a C32DocumentEntityFactory,
	 * and saves the C32DocumentEntity using the C32DocumentDao.
	 */
	@Override
	public void onMessage(Message msg) {
		TextMessage tMsg = (TextMessage)msg;
		AsyncRetrieveMessage aMsg = null;
		
		try {
			aMsg = (AsyncRetrieveMessage)getAsyncMessageFormat().parse(tMsg.getText());
		}
		catch (Exception ex) {
			logger.error("message parsing error for " + msg + ": USER CANNOT BE NOTIFIED (users info is not known)", ex);
			return;
		}
		
		C32DocumentEntity doc = getC32Document(aMsg);

		try {
			if (logger.isDebugEnabled()) {
				logger.debug("Attempting to Persist Domain XML for: " + aMsg.getPatientId());
			}
			updateDocumentWithNewDocument(aMsg.getPatientId(), doc);
		}
		catch (Exception ex) {
			logger.error("error saving c32 document: USER NOT NOTIFIED:" + msg, ex);
		}
	}
	
	protected C32DocumentEntity getC32Document(AsyncRetrieveMessage msg) {
		String xml = null;
		
		try {
			if (logger.isDebugEnabled()) {
				logger.debug("calling getDomainXml for " + msg);
			}
			xml = (String)_nwhinResource.getDomainXml(msg.getPatientId(), msg.getPatientName());
		}
		catch (Exception ex) {
			logger.error("error retrieving c32; attempting to pass this info to user:" + msg, ex);
			return new C32DocumentEntity(msg.getPatientId(), msg.getPatientId(), getNowTimestamp(), BlueButtonConstants.ERROR_C32_STATUS_STRING);
		}

		try {
			return getDocumentFactory().createDocument(msg.getPatientId(), xml);
		}
		catch (Exception ex) {
			logger.error("error parsing c32; attempting to pass this info to user:" + msg, ex);
		}
		return new C32DocumentEntity(msg.getPatientId(), msg.getPatientId(), getNowTimestamp(), BlueButtonConstants.ERROR_C32_PARSE_STATUS_STRING);
	}
	
	protected java.sql.Timestamp getNowTimestamp() {
		return new java.sql.Timestamp(new java.util.Date().getTime());
	}
	
	protected void updateDocumentWithNewDocument(String patientId, C32DocumentEntity newDoc) {
		boolean debugging = logger.isDebugEnabled();
		C32DocumentEntity oldDocument = getOldDocument(newDoc);
		if (oldDocument==null) {
			if (debugging) {
				logger.info("no old document, so inserting new:" + newDoc);
			}
			getC32DocumentDao().insert(newDoc);
			return;
		}
		if (newDoc.getDocument() != null && !newDoc.getDocument().isEmpty()) {
			if (stringsEqualNullSafe(newDoc.getDocumentPatientId(), patientId)) {
				oldDocument.setCreateDate(newDoc.getCreateDate());
				oldDocument.setDocument(newDoc.getDocument());
				oldDocument.setDocumentPatientId(newDoc.getDocumentPatientId());
			}
			else {
				logger.warn("Patient ID's don't match: requested ID:" + patientId + " document ID:" + newDoc.getDocumentPatientId());
				oldDocument.setDocument(BlueButtonConstants.ERROR_PTID_STATUS_STRING);
			}
		} else {
			logger.warn("No valid record available for patient ID: " + patientId);
			oldDocument.setDocument(BlueButtonConstants.UNAVAILABLE_STATUS_STRING);
		}
		if (debugging) {
			logger.debug("merging (updating) document:" + oldDocument);
		}
		getC32DocumentDao().update(oldDocument);
	}

	protected C32DocumentEntity getOldDocument(C32DocumentEntity newDoc) {
    	List<C32DocumentEntity> results = getC32DocumentDao().getAllDocuments(newDoc.getIcn());
    	return getOldDocFromList(results, newDoc);
    }

	protected C32DocumentEntity getOldDocFromList(List<C32DocumentEntity> results, C32DocumentEntity newDoc) {
		if (results!=null && results.size()>0) {
			GregorianCalendar cal = new GregorianCalendar();
			if (results.size()>1) {
				Collections.sort(results);
			}
			for (int i=results.size()-1;i>-1;i--) {
				if (newDoc.getIcn().equals(results.get(i).getIcn()) &&
						BeanUtils.equalsNullSafe(results.get(i).getDocument(), BlueButtonConstants.INCOMPLETE_STATUS_STRING) &&
						datesEqual(cal, results.get(i).getCreateDate(), newDoc.getCreateDate())) {
					return results.get(i);
				}		
			}
		}
		return null;
	}

	protected boolean datesEqual(Calendar cal, Timestamp date1, Timestamp date2) {
		if (date1!=null && date2!=null) {
			cal.setTime(date1);
			int year1 = cal.get(Calendar.YEAR);
			int month1 = cal.get(Calendar.MONTH);
			int day1 = cal.get(Calendar.DATE);

			cal.setTime(date2);
			int year2 = cal.get(Calendar.YEAR);
			int month2 = cal.get(Calendar.MONTH);
			int day2 = cal.get(Calendar.DATE);
			return year1==year2 && month1==month2 && day1==day2;
		}
		return date1==null && date2==null;
	}
    
	protected static boolean stringsEqualNullSafe(String s1, String s2) {
		if (s1==null && s2==null) {
			return true;
		}
		if (s1!=null && s2!=null) {
			String temp1 = s1.trim().toLowerCase();
			String temp2 = s2.trim().toLowerCase();
			return temp1.equals(temp2);
		}
		return false;
	}

}

package org.osehra.das.common.bpm.activity;

import org.osehra.das.common.bpm.engine.ActivityException;
import org.osehra.das.common.bpm.engine.ProcessContext;
import org.osehra.das.common.validation.NullChecker;
import org.osehra.das.common.validator.Validator;
import org.osehra.das.common.validator.ValidatorException;

/**
 * Receive a new message. This is usually the first activity when the process
 * receives a message. If the input is null, it just returns if no validator is
 * present.
 * 
 * @author Julian Jewel
 */
public class Receive extends TransitionImpl {

	/**
	 * The input message is validated by the validator.
	 * @uml.property  name="validator"
	 * @uml.associationEnd  
	 */
	private Validator<Object> validator;

	/**
	 * Execute the activity.
	 * 
	 * @param context
	 *            the process context
	 * @param object
	 *            the input object
	 * @return the input object
	 * @throws ActivityException
	 *             an exception if an error occurred
	 */
	@Override
	protected final Object executeAction(final ProcessContext context,
			final Object object) throws ActivityException {
		// Validate
		if (NullChecker.isNotEmpty(this.validator)) {
			try {
				this.validator.validate(object);
			} catch (final ValidatorException ex) {
				throw new ActivityException(ex);
			}
		}
		return object;
	}

	/**
	 * Set the input validator.
	 * 
	 * @param theValidator
	 *            the validator
	 */
	public final void setValidator(final Validator<Object> theValidator) {
		this.validator = theValidator;
	}
}

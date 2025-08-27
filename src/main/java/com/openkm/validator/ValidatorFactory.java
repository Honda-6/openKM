package com.openkm.validator;

import com.openkm.core.Config;
import com.openkm.core.RepositoryException;
import com.openkm.validator.password.PasswordValidator;

import java.lang.reflect.InvocationTargetException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ValidatorFactory {
	private static Logger log = LoggerFactory.getLogger(ValidatorFactory.class);
	private static PasswordValidator passwordValidator = null;

	/**
	 * Password validator
	 */
	public static PasswordValidator getPasswordValidator() throws RepositoryException {
		if (passwordValidator == null) {
			try {
				log.info("PasswordValidator: {}", Config.VALIDATOR_PASSWORD);
				Object object = Class.forName(Config.VALIDATOR_PASSWORD).getDeclaredConstructor().newInstance();
				passwordValidator = (PasswordValidator) object;
			} catch (ClassNotFoundException | InstantiationException | IllegalAccessException e) {
				log.error(e.getMessage(), e);
				throw new RepositoryException(e.getMessage(), e);
			}
			catch(IllegalArgumentException e){
				log.error("IllegalArgumentException: " + Config.VALIDATOR_PASSWORD, e);
			}
			catch(InvocationTargetException e){
				log.error("InvocationTargetException: " + Config.VALIDATOR_PASSWORD, e);
			}
			catch(NoSuchMethodException e){
				log.error("NoSuchMethodException: " + Config.VALIDATOR_PASSWORD, e);
			}
		}

		return passwordValidator;
	}
}

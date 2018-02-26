# oxAuth is available under the MIT License (2008). See http://opensource.org/licenses/MIT for full text.
# Copyright (c) 2016, Gluu
#
# Author: Yuriy Movchan
#

from org.xdi.service.cdi.util import CdiUtil
from org.xdi.oxauth.security import Identity
from org.xdi.model.custom.script.type.auth import PersonAuthenticationType
from org.xdi.oxauth.service import UserService, AuthenticationService
from org.xdi.util import StringHelper
from org.gluu.site.ldap.persistence.exception import AuthenticationException

import java

class PersonAuthentication(PersonAuthenticationType):
    def __init__(self, currentTimeMillis):
        self.currentTimeMillis = currentTimeMillis

    def init(self, configurationAttributes):
        print "Basic (lock account). Initialization"

        self.invalidLoginCountAttribute = "oxCountInvalidLogin"
        if configurationAttributes.containsKey("invalid_login_count_attribute"):
            self.invalidLoginCountAttribute = configurationAttributes.get("invalid_login_count_attribute").getValue2()
        else:
            print "Basic (lock account). Initialization. Using default attribute"

        self.maximumInvalidLoginAttemps = 3
        if configurationAttributes.containsKey("maximum_invalid_login_attemps"):
            self.maximumInvalidLoginAttemps = StringHelper.toInteger(configurationAttributes.get("maximum_invalid_login_attemps").getValue2())
        else:
            print "Basic (lock account). Initialization. Using default number attempts"

        print "Basic (lock account). Initialized successfully. invalid_login_count_attribute: '%s', maximum_invalid_login_attemps: '%s'" % (self.invalidLoginCountAttribute, self.maximumInvalidLoginAttemps)

        return True   

    def destroy(self, configurationAttributes):
        print "Basic (lock account). Destroy"
        print "Basic (lock account). Destroyed successfully"
        return True

    def getApiVersion(self):
        return 1

    def isValidAuthenticationMethod(self, usageType, configurationAttributes):
        return True

    def getAlternativeAuthenticationMethod(self, usageType, configurationAttributes):
        return None

    def authenticate(self, configurationAttributes, requestParameters, step):
        authenticationService = CdiUtil.bean(AuthenticationService)

        if step == 1:
            print "Basic (lock account). Authenticate for step 1"

            identity = CdiUtil.bean(Identity)
            credentials = identity.getCredentials()
            user_name = credentials.getUsername()
            user_password = credentials.getPassword()

            logged_in = False
            if (StringHelper.isNotEmptyString(user_name) and StringHelper.isNotEmptyString(user_password)):
                try:
                    logged_in = authenticationService.authenticate(user_name, user_password)
                except AuthenticationException:
                    print "Basic (lock account). Authenticate. Failed to authenticate user '%s'" % user_name

            if (not logged_in):
                countInvalidLoginArributeValue = self.getUserAttributeValue(user_name, self.invalidLoginCountAttribute)
                countInvalidLogin = StringHelper.toInteger(countInvalidLoginArributeValue, 0)

                if countInvalidLogin < self.maximumInvalidLoginAttemps:
                    countInvalidLogin = countInvalidLogin + 1
                    self.setUserAttributeValue(user_name, self.invalidLoginCountAttribute, StringHelper.toString(countInvalidLogin))

                if countInvalidLogin >= self.maximumInvalidLoginAttemps:
                    self.lockUser(user_name)
                    
                return False

            self.setUserAttributeValue(user_name, self.invalidLoginCountAttribute, StringHelper.toString(0))

            return True
        else:
            return False

    def prepareForStep(self, configurationAttributes, requestParameters, step):
        if step == 1:
            print "Basic (lock account). Prepare for Step 1"
            return True
        else:
            return False

    def getExtraParametersForStep(self, configurationAttributes, step):
        return None

    def getCountAuthenticationSteps(self, configurationAttributes):
        return 1

    def getPageForStep(self, configurationAttributes, step):
        return ""

    def logout(self, configurationAttributes, requestParameters):
        return True

    def getUserAttributeValue(self, user_name, attribute_name):
        if StringHelper.isEmpty(user_name):
            return None

        userService = CdiUtil.bean(UserService)

        find_user_by_uid = userService.getUser(user_name, attribute_name)
        if find_user_by_uid == None:
            return None

        custom_attribute_value = userService.getCustomAttribute(find_user_by_uid, attribute_name)
        if custom_attribute_value == None:
            return None
        
        attribute_value = custom_attribute_value.getValue()

        print "Basic (lock account). Get user attribute. User's '%s' attribute '%s' value is '%s'" % (user_name, attribute_name, attribute_value)

        return attribute_value

    def setUserAttributeValue(self, user_name, attribute_name, attribute_value):
        if StringHelper.isEmpty(user_name):
            return None

        userService = CdiUtil.bean(UserService)

        find_user_by_uid = userService.getUser(user_name)
        if find_user_by_uid == None:
            return None
        
        userService.setCustomAttribute(find_user_by_uid, attribute_name, attribute_value)
        updated_user = userService.updateUser(find_user_by_uid)

        print "Basic (lock account). Set user attribute. User's '%s' attribute '%s' value is '%s'" % (user_name, attribute_name, attribute_value)

        return updated_user

    def lockUser(self, user_name):
        if StringHelper.isEmpty(user_name):
            return None

        userService = CdiUtil.bean(UserService)

        find_user_by_uid = userService.getUser(user_name)
        if (find_user_by_uid == None):
            return None

        status_attribute_value = userService.getCustomAttribute(find_user_by_uid, "gluuStatus")
        if status_attribute_value != None:
            user_status = status_attribute_value.getValue()
            if StringHelper.equals(user_status, "inactive"):
                print "Basic (lock account). Lock user. User '%s' locked already" % user_name
                return
        
        userService.setCustomAttribute(find_user_by_uid, "gluuStatus", "inactive")
        updated_user = userService.updateUser(find_user_by_uid)

        print "Basic (lock account). Lock user. User '%s' locked" % user_name

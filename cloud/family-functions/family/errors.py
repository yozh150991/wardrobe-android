class FamilyError(Exception):
    def __init__(self, code, message, reason=None):
        super().__init__(message)
        self.code = code
        self.reason = reason


def require(condition, message="Invalid family data", code="invalid-argument", reason=None):
    if not condition:
        raise FamilyError(code, message, reason)

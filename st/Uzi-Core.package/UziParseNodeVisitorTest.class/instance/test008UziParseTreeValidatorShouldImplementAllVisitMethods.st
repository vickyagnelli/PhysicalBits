autogenerated-tests
test008UziParseTreeValidatorShouldImplementAllVisitMethods
	| errors selectors |
	selectors := self allSelectorsFor: UziParseTreeValidator.
	errors := self missingNodes: selectors.
	self assert: errors isEmpty
		description: 'UziParseTreeValidator is missing some critical methods'
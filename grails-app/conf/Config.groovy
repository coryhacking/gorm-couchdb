
grails.gorm.default.constraints = {

	'*'(nullable: true, blank: false)

	'description'(nullable: true, blank: false, size:10..50)

}

grails.gorm.failOnError = true
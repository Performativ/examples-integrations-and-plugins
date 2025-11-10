#### Bulk ingestion

* Performativ supports a task-oriented API for ingestion work in bulk. 

* You create an ingestion task per entity or relation => you get back a task-id and a presigned upload url
* You upload csv (or gzip csv) to the location
* You start the job
* You query the job status
* You can get per row errors back
* If there are errors only the chunk is rejected
* ( it is not batch atomic - whatever made it in made it in.)
* ( items have external_ids which are used for both relations and for PK in create/update/delete)
* ( for deletions we support a deletion flag)



* We have external ID on: 
* Client, User, Person

* but missing on
* Advisor, Business

# Effective parent child relationships with caching in JPA

## Motivation

If you are working with JAVA and are using **JAVA EE** or just **JPA**, this should be interesting for you.

Here is a **simple scenario**. You have many employees and each of them belongs to some department. Each department can have many different employees. It's a classic. You want to display some list of employees with name of the department.

When you *normally* retrieve list of employees from DB using JPA, it generates number (N+1) of queries:
* First query to retrieve a list of employees
* For each employee it generates one query to retrieve name of the deparment

Which means, if you retrieve 100 employees, JPA will generate 100+1=**101 queries**. That is so inefficient. We can do that with **two queries** and this article describes how.

## Demonstration of a problem

First of all, I am stuck with JAVA EE 6. I have read a bit about Entity graphs but I did not like it. There were some bugs reported and it seemed like I just could not use **native SQL** to retrieve entities the way I wanted to. So I was thinking about the alternatives and I thought about **caching**.

Let's see how it works by *default*. I will try to retrieve employees with salary greater than 10 000. I'll use a simple query:
```sql
select e.*
from EMPLOYEES e
where 1=1
and E.SALARY > 10000
```

I made this query as a named query:
```java
@NamedNativeQuery(name="Employee.getHigherPayed", resultClass=Employee.class, query=
		"select e.*\n" + 
		"from EMPLOYEES e\n" + 
		"where 1=1\n" + 
		"and E.SALARY > 10000\n" + 
		"")
```

Call the query from EJB:
```java
    public List<Employee> getEmployeesHigherPayed() {
    	return em.createNamedQuery("Employee.getHigherPayed", 
    			Employee.class).getResultList();
    }
```

Here is the log:
```
2017-09-19T20:42:06.542+0200|Fine: select e.*
from EMPLOYEES e
where 1=1
and E.SALARY > 10000
2017-09-19T20:42:06.548+0200|Fine: SELECT DEPARTMENT_ID, DEPARTMENT_NAME, MANAGER_ID FROM DEPARTMENTS WHERE (DEPARTMENT_ID = ?)
	bind => [90]
2017-09-19T20:42:06.550+0200|Fine: SELECT DEPARTMENT_ID, DEPARTMENT_NAME, MANAGER_ID FROM DEPARTMENTS WHERE (DEPARTMENT_ID = ?)
	bind => [100]
2017-09-19T20:42:06.553+0200|Fine: SELECT DEPARTMENT_ID, DEPARTMENT_NAME, MANAGER_ID FROM DEPARTMENTS WHERE (DEPARTMENT_ID = ?)
	bind => [30]
2017-09-19T20:42:06.554+0200|Fine: SELECT DEPARTMENT_ID, DEPARTMENT_NAME, MANAGER_ID FROM DEPARTMENTS WHERE (DEPARTMENT_ID = ?)
	bind => [80]
2017-09-19T20:42:06.556+0200|Fine: SELECT DEPARTMENT_ID, DEPARTMENT_NAME, MANAGER_ID FROM DEPARTMENTS WHERE (DEPARTMENT_ID = ?)
	bind => [20]
2017-09-19T20:42:06.558+0200|Fine: SELECT DEPARTMENT_ID, DEPARTMENT_NAME, MANAGER_ID FROM DEPARTMENTS WHERE (DEPARTMENT_ID = ?)
	bind => [110]
```
As mentioned earlier, first query is the one for retrieving a list of employees and all the other ones are retrieving department for each employee. Nobody wants this to work like that.

## Solution using caching

After the native query was executed, all the departments are **immediately retrieved** from the database by executing queries because each employee has ```@ManyToOne``` relationship to Deparment. We can say we want to fetch it lazy so it is not retrieved immediately but when we first ask for it:
```java
@ManyToOne(fetch=FetchType.LAZY) //don't fetch immediately in order to use power of caching
@JoinColumn(name="DEPARTMENT_ID")
private Department department;
```
I know that I'll need departments of all the employees which I received. I can make a native query for these Deparments:
```java
@NamedNativeQuery(name="Department.Employee.getHigherPayed", resultClass=Department.class, query=
		"select distinct d.*\n" + 
		"from EMPLOYEES e\n" + 
		"join DEPARTMENTS d  on  E.DEPARTMENT_ID =   D.DEPARTMENT_ID\n" + 
		"where 1=1\n" + 
		"and E.SALARY > 10000\n" + 
		"")
```
Which I'll call like this:
```java
    public List<Department> getDepartmentsForEmployeesHigherPayed() {
    	return em.createNamedQuery("Department.Employee.getHigherPayed", 
    			Department.class).getResultList();
    }    
```
Now I'll make a new method which will first get all the employees, then it will get all the deparments to which these employees belong to. And then I'll go through each employee and try to get its department and this deparment. Entity manager knows the ID of the department (it is saved in each employee) and it will check first if it has a department with this id in its **cache** and if it does, it will get it from cache and otherwise it will retrieve it from the database like it has done in earlier examples. This is what we should do:
```java
    public List<Employee> getEmployeesHigherPayedWithDeparments() {
    	List<Employee> emps = getEmployeesHigherPayed(); //get employees
    	List<Department> deps = getDepartmentsForEmployeesHigherPayed(); //get departments to which retrieved employees relate to
    	for (Employee e: emps) {
    		e.getDepartment(); //get the department from cache now while we are in the transaction
    	}
    	return emps;
    }
```
And here is the log:
```
2017-09-20T10:32:38.744+0200|Fine: select e.*
from EMPLOYEES e
where 1=1
and E.SALARY > 10000

2017-09-20T10:32:44.128+0200|Fine: select distinct d.*
from EMPLOYEES e
join DEPARTMENTS d  on  E.DEPARTMENT_ID =   D.DEPARTMENT_ID
where 1=1
and E.SALARY > 10000
```
Works like a charm :) I only need **two queries to the database** which is the way it should be and I'll just need to play a bit with caching in order to make it work which is not that big deal: I just need to touch a field to retrieve data from cache.

## Missing table entities 

If we know that we won't need to update entities with merge or persist, we can make an entity which does not correspond to a table and use it just for mapping SQL data rows to list of entities in JAVA. You can use something like this:
```java
@Entity
@NamedNativeQueries({
	@NamedNativeQuery(name="EmployeeWithDeparment.getHigherPayed", resultClass=EmployeeWithDeparment.class, query=
			"select E.EMPLOYEE_ID, E.FIRST_NAME, E.LAST_NAME, E.SALARY, D.DEPARTMENT_NAME \r\n" + 
			"from EMPLOYEES e \r\n" + 
			"join DEPARTMENTS d  on  E.DEPARTMENT_ID =   D.DEPARTMENT_ID \r\n" + 
			"where 1=1 \r\n" + 
			"and E.SALARY > 10000 \r\n" + 
			"")
})
public class EmployeeWithDeparment implements Serializable {
	private static final long serialVersionUID = 1L;

	@Id
	@Column(name="EMPLOYEE_ID")
	private Long employeeId;

	@Column(name="FIRST_NAME")
	private String firstName;

	@Column(name="LAST_NAME")
	private String lastName;

	private BigDecimal salary;

	@Column(name="DEPARTMENT_NAME")
	private String departmentName;
```

As you can see, we are joining tables and we are using data from various tables in one entity. We just need to provide mentioned columns in our entity. We can not merge or persist this entity as entity manager would try to make an UPDATE or INSERT SQL statement on table EmployeeWithDeparment which does not actually exist. We can still write our own UPDATE or INSERT statements if we want to. This technique is mostly useful if we are just going to read data from the database and want to have a convenient way of mapping SQL data to JAVA object.

## Environment setup
* Eclipse Oxygen
* JAVA 1.8
* Oracle 12c database with HR sheme (you can download sample database). I recommend using docker.
* Glassfish 4.1 with JDBC connection called "jdbc/hrDS"
* Git

## Conclusion

I have demonstrated how we can **efficiently** retrieve data for **entities** which have a **parent-child  relationships**. Here we went **from N+1 queries**, where N is the number of child entities, **to only 2 queries**. This is significant improvement: your users will be happy (application will be faster), your DBA will be happy (less queries and less pressure on the database) and you will spend less electrical energy for this which makes Mother Earth very happy. If you have any friends and colleagues who care about this stuff, share this information with them.

Here is a **quick summary** what we need to do in order to make it work:
* Make a child-parent relationship with lazy fetching: ```@ManyToOne(fetch=FetchType.LAZY)```
* Make a query for fetching children first and then a query to fetch their parents (only the ones which are needed). Both children and parents are cached.
* Go through each child and just get its parent to invoke it from the cache inside EJB method. If you fail to do this, it will probably happen that later when application gets the parent, JPA will go to the database to retrieve it which is the problem we were trying to solve in the first place.
* If your method does not require transaction, which is when you are not changing any data in the entities, you can additionally annotate your EJB method with ```@TransactionAttribute(TransactionAttributeType.NOT_SUPPORTED)``` which makes things even faster.

GitHub repository:

https://github.com/mih0vil/ParentChildCachingJpa.git



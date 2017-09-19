package hr.mihovil.ejb;

import java.util.List;

import javax.ejb.LocalBean;
import javax.ejb.Stateless;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;

import hr.mih0vil.parentchild.Department;
import hr.mih0vil.parentchild.Employee;

/**
 * Session Bean implementation class EmployeeService
 */
@Stateless
@LocalBean
public class EmployeeService {
	
	@PersistenceContext
	EntityManager em;

    public EmployeeService() {
    	System.out.println("EmployeeService()");
    }

    public List<Employee> getEmployeesHigherPayed() {
    	return em.createNamedQuery("Employee.getHigherPayed", 
    			Employee.class).getResultList();
    }
    
    public List<Department> getDepartmentsForEmployeesHigherPayed() {
    	return em.createNamedQuery("Department.Employee.getHigherPayed", 
    			Department.class).getResultList();
    }
    
}

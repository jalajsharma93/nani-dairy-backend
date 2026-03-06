package net.nani.dairy.sales;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface SaleRepository extends JpaRepository<SaleEntity, String> {
    List<SaleEntity> findByDispatchDate(LocalDate dispatchDate);
    List<SaleEntity> findByDispatchDateBetween(LocalDate dispatchDateStart, LocalDate dispatchDateEnd);
    List<SaleEntity> findByDispatchDateBetweenAndCustomerId(LocalDate dispatchDateStart, LocalDate dispatchDateEnd, String customerId);
    List<SaleEntity> findByCustomerId(String customerId);
    List<SaleEntity> findByDispatchDateBeforeAndCustomerId(LocalDate dispatchDate, String customerId);
    List<SaleEntity> findByDispatchDateLessThanEqualAndCustomerId(LocalDate dispatchDate, String customerId);
    List<SaleEntity> findByDispatchDateBetweenAndCustomerTypeAndCustomerNameIgnoreCase(
            LocalDate dispatchDateStart,
            LocalDate dispatchDateEnd,
            CustomerType customerType,
            String customerName
    );
    List<SaleEntity> findByCustomerTypeAndCustomerNameIgnoreCase(CustomerType customerType, String customerName);
    List<SaleEntity> findByDispatchDateBeforeAndCustomerTypeAndCustomerNameIgnoreCase(
            LocalDate dispatchDate,
            CustomerType customerType,
            String customerName
    );
    List<SaleEntity> findByDispatchDateLessThanEqualAndCustomerTypeAndCustomerNameIgnoreCase(
            LocalDate dispatchDate,
            CustomerType customerType,
            String customerName
    );

    List<SaleEntity> findByCustomerType(CustomerType customerType);

    List<SaleEntity> findByProductType(ProductType productType);

    List<SaleEntity> findByDispatchDateAndCustomerType(LocalDate dispatchDate, CustomerType customerType);

    List<SaleEntity> findByDispatchDateAndProductType(LocalDate dispatchDate, ProductType productType);

    List<SaleEntity> findByCustomerTypeAndProductType(CustomerType customerType, ProductType productType);

    List<SaleEntity> findByDispatchDateAndCustomerTypeAndProductType(LocalDate dispatchDate, CustomerType customerType, ProductType productType);

    List<SaleEntity> findByDispatchDateBetweenAndCustomerTypeAndProductType(
            LocalDate dispatchDateStart,
            LocalDate dispatchDateEnd,
            CustomerType customerType,
            ProductType productType
    );

    List<SaleEntity> findByDispatchDateBetweenAndCustomerTypeAndCustomerNameIgnoreCaseAndProductTypeAndReconciledFalse(
            LocalDate dispatchDateStart,
            LocalDate dispatchDateEnd,
            CustomerType customerType,
            String customerName,
            ProductType productType
    );
}

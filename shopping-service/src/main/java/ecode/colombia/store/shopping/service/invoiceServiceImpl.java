package ecode.colombia.store.shopping.service;

import ecode.colombia.store.shopping.client.CustomerClient;
import ecode.colombia.store.shopping.client.ProductClient;
import ecode.colombia.store.shopping.entity.Invoice;
import ecode.colombia.store.shopping.entity.InvoiceItem;
import ecode.colombia.store.shopping.model.Customer;
import ecode.colombia.store.shopping.model.Product;
import ecode.colombia.store.shopping.repository.InvoiceItemsRepository;
import ecode.colombia.store.shopping.repository.InvoiceRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cloud.client.circuitbreaker.CircuitBreakerFactory;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Slf4j
@Service
public class invoiceServiceImpl implements InvoiceService {
    @Autowired
    private InvoiceRepository invoiceRepository;

    @Autowired
    InvoiceItemsRepository invoiceItemsRepository;

    @Autowired
    ProductClient productClient;

    @Autowired
    CustomerClient customerClient;

    private final CircuitBreakerFactory circuitBreakerFactory;

    public invoiceServiceImpl(CircuitBreakerFactory circuitBreakerFactory) {
        this.circuitBreakerFactory = circuitBreakerFactory;
    }

    @Override
    public List<Invoice> findInvoiceAll() {
        return invoiceRepository.findAll();
    }


    @Override
    public Invoice createInvoice(Invoice invoice) {
        Invoice invoiceDB = invoiceRepository.findByInvoiceNumber(invoice.getInvoiceNumber());
        if (invoiceDB !=null){
            return  invoiceDB;
        }
        invoice.setState("Created");
        invoiceDB = invoiceRepository.save(invoice);
        invoiceDB.getItems().forEach( invoiceItem -> {
            productClient.updateStockProduct(invoiceItem.getProductId(), invoiceItem.getQuantity() * -1);
        });
        return invoiceDB;
    }


    @Override
    public Invoice updateInvoice(Invoice invoice) {
        Invoice invoiceDB = getInvoice(invoice.getId());
        if (invoiceDB == null){
            return  null;
        }
        invoiceDB.setCustomerId(invoice.getCustomerId());
        invoiceDB.setDescription(invoice.getDescription());
        invoiceDB.setInvoiceNumber(invoice.getInvoiceNumber());
        invoiceDB.getItems().clear();
        invoiceDB.setItems(invoice.getItems());
        return invoiceRepository.save(invoiceDB);
    }


    @Override
    public Invoice deleteInvoice(Invoice invoice) {
        Invoice invoiceDB = getInvoice(invoice.getId());
        if (invoiceDB == null){
            return  null;
        }
        invoiceDB.setState("DELETED");
        return invoiceRepository.save(invoiceDB);
    }

    @Override
    public Invoice getInvoice(Long id) {
        Invoice invoice = invoiceRepository.findById(id).orElse(null);
        if(null != invoice){
            Customer customer = circuitBreakerFactory.create("getCustomer").run(
                    () -> customerClient.getCustomer(invoice.getCustomerId()).getBody(),
                    t -> new Customer());
            invoice.setCustomer(customer);
            List<InvoiceItem> listItems = invoice.getItems().stream().map(invoiceItem -> {
                Product product = circuitBreakerFactory.create("getProduct").run(
                        () -> productClient.getProduct(invoiceItem.getProductId()).getBody(),
                        t -> new Product());
                invoiceItem.setProduct(product);
                return invoiceItem;
            }).collect(Collectors.toList());
            invoice.setItems(listItems);
        }
        return invoice;
    }
}

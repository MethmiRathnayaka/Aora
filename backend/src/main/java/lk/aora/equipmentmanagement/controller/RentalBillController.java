package lk.aora.equipmentmanagement.controller;

import java.time.LocalDate;
import lk.aora.equipmentmanagement.dto.transfer.RentalBillDto;
import lk.aora.equipmentmanagement.service.RentalBillService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/rental-bills")
public class RentalBillController {
    private final RentalBillService service;
    public RentalBillController(RentalBillService service) { this.service = service; }

    @GetMapping
    public RentalBillDto bill(@RequestParam("worksiteId") Long worksiteId,
            @RequestParam("fromDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate fromDate,
            @RequestParam("toDate") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate toDate) {
        return service.bill(worksiteId, fromDate, toDate);
    }
}

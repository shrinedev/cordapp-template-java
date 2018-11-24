package com.template;

import net.corda.core.contracts.CommandData;
import net.corda.core.contracts.Contract;
import net.corda.core.transactions.LedgerTransaction;

import net.corda.core.contracts.CommandWithParties;
import net.corda.core.identity.Party;

import java.security.PublicKey;
import java.util.Arrays;
import java.util.List;

import static net.corda.core.contracts.ContractsDSL.requireSingleCommand;

// ************
// * Contract *
// ************
public class IOUContract implements Contract {
    public static final String ID = "com.template.IOUContract";

    /**
     * Our Create command.
     *  Commands serve two functions:
     *   - Indicate a transaction's intent. This allows us to perform different
     *     verification for different types of transactions: e.g. creating IOU
     *     vs. redeeming IOU.
     *   - Allow us to define required signers for the transaction. e.g. IOU
     *     creation could only require signature of lender, transfer of IOU could
     *     require signatures from both borrower and lender
     */
    public static class Create implements CommandData {
    }

    /**
     *  All contracts must implement the verify method.
     *  Goals of verify:
     *   - Throw IllegalArgumentException if transaction is considered invalid
     *   - Does not throw exception if transaction is valid
     *  Verify only has access to the contents of the transaction:
     *   - tx.inputs
     *   - tx.outputs
     *   - tx.commands
     *  And (not used here):
     *   - tx attachments
     *   - tx time-window
     */
    @Override
    public void verify(LedgerTransaction tx) {

        // First verify the transaction contains the Create command.
        final CommandWithParties<IOUContract.Create> command = requireSingleCommand(tx.getCommands(), IOUContract.Create.class);

        // Constraints on the shape of the transaction.
        if (!tx.getInputs().isEmpty())
            throw new IllegalArgumentException("No inputs should be consumed when issuing an IOU.");
        if (!(tx.getOutputs().size() == 1))
            throw new IllegalArgumentException("There should be one output state of type IOUState.");

        // IOU-specific constraints.
        final IOUState output = tx.outputsOfType(IOUState.class).get(0);
        final Party lender = output.getLender();
        final Party borrower = output.getBorrower();
        if (output.getValue() <= 0)
            throw new IllegalArgumentException("The IOU's value must be non-negative.");
        if (lender.equals(borrower))
            throw new IllegalArgumentException("The lender and the borrower cannot be the same entity.");

        // Constraints on the signers.
        final List<PublicKey> requiredSigners = command.getSigners();
        final List<PublicKey> expectedSigners = Arrays.asList(borrower.getOwningKey(), lender.getOwningKey());
        if (requiredSigners.size() != 2)
            throw new IllegalArgumentException("There must be two signers.");
        if (!(requiredSigners.containsAll(expectedSigners)))
            throw new IllegalArgumentException("The borrower and lender must be signers.");

    }
}